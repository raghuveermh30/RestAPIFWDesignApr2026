# PICT playbook (Phases 1–4 detail)

## Phase 1 — mandatory classification steps
**Flag configuration factors (`is_config: true`).** Mark a factor config when it represents environment/setup state, not a runtime action — feature flags/grants (`POS-119192#2025-03 ON/OFF`), business/store config, register/terminal type, platform/form factor/OS version, app network mode (online/offline), gateway communication mode (local/cloud), any "Configuration(s)"/"Business Configuration" group. These are the factors combinations tend to omit, so:
- every config factor stays a PICT parameter with all its values;
- do not reduce it to a single default just because the plan mentions one setting — both/all states stay unless the plan says a state is impossible (then a Phase 2 constraint, with reason);
- keep the `is_config` flag (Phase 4 uses it for coverage, Phase 5 records it).

**Collapse guard-invariant factors (prevents over-expansion).** Read the plan's *Design decisions / Root cause / Fix* to find what the change keys on (the controlling guard/code path). For any factor the plan states does **not** affect the changed outcome:
- mark `guard_invariant: true` and reduce it to a single representative value (plus one extra only if it is itself a config factor needing coverage, or the plan flags a value as risky);
- record each collapsed factor + the justifying plan line under `guard_invariant[]`;
- be conservative — collapse only with explicit plan evidence; when unsure, keep the factor and let Phase 4 risk-weighting handle it.

## Phase 2 — model
1. One line per parameter: `Factor Name: v1, v2, v3`. Include every config parameter with all values — never omit or single-value them here.
2. **Constraints** (derive from plan; this prevents impossible combos): e.g. `IF [Tender] = "GiftCard" THEN [Item Type] <> "GiftCard";` — add for every mutually-exclusive or precondition pair in *Design decisions / Risks*. Constrain a config factor only on explicit plan evidence (record the source line); never add a constraint that freezes a config factor to one value just to shrink rows.
3. **Sub-models / strength** for risky clusters: `{ Deal Type, Stacking } @ 3`. Prefer raising strength on clusters of P0 factors (e.g. Configurations × Payment State × Gateway Mode). **Sub-model guard:** PICT rejects `{ <all params> } @ N` when the sub-model covers every parameter (no "outstanding parameters" remain). `build_pict_model.py` automatically skips the sub-model when `len(p0_params) == total_params` and instead runs PICT at global strength 3 (falling back to 2 on failure). Never add a sub-model manually that covers all parameters.
4. Default interaction strength = 2 (pairwise); raise to 3 only for high-risk or all-P0 clusters.
5. **Constraint string sanitization:** PICT requires double-quoted value strings (`"value"`) inside `IF/THEN` constraints. `build_pict_model.py` preserves double-quotes when reading `pict_constraint` fields from the factors JSON (`sanitize_name` is used, not `sanitize_value`). Never pass constraints through `sanitize_value` — it converts `"` to `'`, causing PICT parse errors.

## Phase 4 — optimization (apply in order, keep minimal)
1. **Seed must-cover rows**: each explicit Acceptance Criteria / Test Plan scenario becomes a mandatory row (PICT `/e:` seed or append + de-dup). Tag `source: plan-seed`.
2. **Prune** rows violating a real-world rule the constraints missed.
3. **Risk-weight by priority**: tag each row `risk: high|med|low`; any row touching a P0 Negative/Boundary value is at least high. Every high-risk pair appears once.
4. **Negative/Boundary coverage**: ≥1 row exercises each factor's Negative value and each Boundary value.
5. **Priority + config coverage (MANDATORY)**: P0 → every value in ≥1 combination (incl. every config factor: flag ON and OFF, every platform, online and offline, Local and Cloud); P1 → every key value (each Positive + each Negative/Boundary); P2 → ≥1 representative. Every `is_config` parameter gets full coverage regardless of tier. If a value is missing (seeding/pruning skew), add/adjust a row (re-run with `/e:` seed if needed). A value may be uncovered only if a Phase 2 constraint makes it impossible → note `excluded_by_constraint` with the plan reason.
6. Record before/after counts (`pict_rows` vs `final_rows`).

## Worked example — POS-181833 (guard-invariant collapse)
Plan: race condition fixed by disabling "Return All Eligible Items" while `scanInQueue > 0`. Plan's Root cause/Fix state the guard is `scanInQueue`-based and **independent of order type, payment-screen state, and form factor** (those only affect other pre-existing disable conditions).

Stage-1 factors included `FeatureFlag`, `OrderType`, `FormFactor`, `PaymentScreen`, `processBarcodeOutcome`, `ClickTiming`.

Phase 1 collapse: for the in-flight-disable guarantee, `OrderType`/`FormFactor`/`PaymentScreen` do not change the outcome, so collapse each. Without collapse PICT emitted ~7 rows (C001–C007) differing only by those invariant factors → became near-duplicate cases TC-S004–S007. After collapse: ~2 in-flight-disable rows (one Desktop, one Mobile to retain the two button instances) plus genuinely distinct rows (`processBarcodeOutcome=error`; FF-disabled baseline; OrderId-vs-scanInQueue boundary).

```json
"guard_invariant": [
  {"factor":"OrderType","collapsed_to":"Return","evidence":"Plan Fix: guard is scanInQueue-based, order-type independent"},
  {"factor":"PaymentScreen","collapsed_to":"out","evidence":"Plan Root cause: payment-screen is a separate pre-existing condition, not the race guard"},
  {"factor":"FormFactor","collapsed_to":"Desktop+Mobile","evidence":"Two button instances rendered; kept both, dropped further variation"}
]
```
Rule of thumb: collapse only when the plan explicitly proves the changed behavior does not branch on the factor; when in doubt, keep it.
