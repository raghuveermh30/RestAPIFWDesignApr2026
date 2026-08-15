---
name: manh-product-influencing-factor-identifier
version: 2026-07-30
description: Stage 1 of the plan-driven test generation chain. Given a plan, walk the per-product influencing-factors master index (a prioritized P0/P1/P2 catalog of factor-group lenses), surgically select the factors the plan touches, derive the concrete plan-specific values, carry each factor's priority forward, confirm PICT vs SEED placement with the user (Phase 4a), run inflation checks + quality recovery (Phase 4d), and finalize interactively before emitting. P0 factors are never silently dropped. PICT vs SEED placement is always user-confirmed — the master index provides recommendations, never final decisions. OC drops always produce generic seeds or recorded assertions — coverage is never silently abandoned. Use when a plan exists and you need the prioritized factor set before generating test combinations with manh-product-test-combination-generator.
---

<what-to-do>

Turn a plan into a **scoped, justified, user-approved list of influencing factors + values** — the entry point of the test chain. Be surgical: keep only factors the plan exercises, but never silently drop a P0 factor (record a reason if you exclude one). Never ship a factor the user did not confirm.

Run the phases in order. Phase 0, Phase 4a, and Phase 5 are blocking gates. Phase 4c is a blocking gate when `COMBO_EST > COMBO_BUDGET`. Phase 4d is mandatory after every OC drop — it cannot be skipped even when under budget.

**Q&A protocol (all phases).** Ask questions **one at a time**. For each question present 2–4 numbered options, label the recommended choice with **"Recommendation:"** + a brief rationale, and wait for the user's answer before asking the next question.

**Phase 0 — Resolve inputs (blocking; do FIRST).** Full protocol + mandatory gate: [references/resolve-inputs.md](./references/resolve-inputs.md).

**Phase 1 — Load.** Parse the master index `MACHINE_BLOCK` (else the per-tier tables) into `CATALOG = [{group, priority, lens, reference_values[]}]`. Load the plan. Gate: both loaded, `CATALOG` non-empty, every entry has a P0/P1/P2 priority.

**Phase 2 — Derive the feature(s) under test.** From the plan title/impacted-files/code-changes/design/risks, name the feature(s) in plain language into `FEATURES`. Collect `PLAN_EVIDENCE` (files/areas/risk signals). Optionally use `module-index.md` to map changed files to areas (evidence only).

**Phase 3 — Select candidate groups (priority-aware).** Walk every `CATALOG` group; apply the per-tier keep test below. Record plan evidence per candidate; carry `priority` forward. Apply the cross-group dedup check at the end of this phase.

**Per-tier keep test:**
- **P0:** Keep unless the plan explicitly proves the group irrelevant. Exclusion requires a one-line recorded reason. Never silently drop.
- **P1:** Keep only if the plan contains at least one of: a modified file the group's lens directly questions, an explicit design decision the lens addresses, or a named risk/edge case the lens captures. "Plausibly related" is not sufficient.
- **P2:** Keep only if the plan **explicitly mentions** the non-functional concern this group covers. Do not include P2 groups on inference alone.

**Cross-group dedup check (end of Phase 3):** Scan for pairs where two groups produce the same factor from the same plan evidence. For each flagged pair: present both groups, state which factor would duplicate, and recommend which to keep. Do not proceed to Phase 4 until all dedup conflicts are resolved.

**Phase 4 — Derive concrete factors + values (surgical core).** For each candidate group derive the concrete factor(s) the plan implies, carrying the group's priority. Derive values grounded in evidence (changed code enums/switch-arms/config keys/flag states/error codes first, then plan text). Classify each value Positive/Negative/Boundary. Tag `confidence: high|med|low`; push uncertainties to `OPEN_QUESTIONS`. Any split-vs-merge question MUST use the decision rule + default from [references/split-vs-merge.md](./references/split-vs-merge.md).

Apply these guards during derivation:
- **Single-value factors:** move to `fixed_context[]`; do not include in `factors[]`.
- **Derived-outcome factors (rule E8):** record as assertions in the parent factor's `evidence`; not a PICT parameter.
- **Duplicate values across factors:** merge into one factor; not two.

Conditions fitting no group go to `gaps[]` with a suggested tier — never a bare value. Ensure every selected P0 group yields ≥1 factor.

**Phase 4a — PICT vs SEED confirmation (blocking; run before Phase 4b).** Present the proposed placement for each factor in a single consolidated table. Make exactly **one** `askUser()` call presenting the table and asking whether the user wants any values moved between PICT/SEED/ASSERT or confirms the table as-is. Gate: every factor must have an explicit classification confirmed before Phase 4b runs. Full placement rules: see the PICT vs SEED rules in the supporting-info section below.

**Phase 4b — Inflation check (blocking; run before Phase 4c).** Run the seven surgical checks in order from [references/oc-checks.md](./references/oc-checks.md). Auto-apply each — do not ask the user. Show a brief decision summary before proceeding to Phase 4c.

**Phase 4c — Combination budget check + optimization loop (blocking).** Compute `COMBO_EST`, `COMBO_BUDGET`, and `PRACTICAL_TOTAL` per the formulas in supporting-info. Display the budget status line (always). If `COMBO_EST ≤ COMBO_BUDGET`, proceed to Phase 4d. If over budget, run the OC optimization loop from [references/oc-checks.md](./references/oc-checks.md) before proceeding. Emit is forbidden until the user explicitly confirms the final count.

**Phase 4d — Quality recovery check (mandatory; run after every OC drop).** Build the coverage debt ledger, check MG1–MG10, apply the generic seed protocol, apply gap rules G1–G5, and verify P2 non-functional coverage. Full protocol: [references/quality-recovery.md](./references/quality-recovery.md). Display the recovery summary before proceeding to Phase 5.

**Phase 5 — Finalize interactively (blocking).** Resolve all `OPEN_QUESTIONS` one-at-a-time first (before showing the factor table). Present the derived factors as a table `Group | Priority | Factor | Proposed values | Pos/Neg/Boundary | Confidence | Evidence`, sorted P0 first. Make exactly **one** `askUser()` call for the user to add, remove, rename, re-classify, or confirm. Apply any changes, re-present only changed rows, then make one final `askUser()` to confirm. Gate: capture `FINAL_FACTORS`; if no sign-off, STOP — do not write.

**Phase 6 — Emit.** Write two separate files to `<out_dir>` resolved in Phase 0. Full artifact layout, JSON schema, field rules, and Final Gate: [references/output-format.md](./references/output-format.md). After writing the JSON artifact, run the Final Gate validator and confirm exit code `0` before declaring done:
```bash
python3 references/validate-output.py <path-to-influencing-factors.json>
```

</what-to-do>

<supporting-info>

## Resolve product-dir

Identify `{product}` slug from the user's input (e.g. `pos`, `mascp`, `oms`, `sif`, `matm`, `wms`). Ask once if not stated.
Resolve `{product-repo}` from the static Product → Active repo table below.
Construct: `product-dir` = `{workspace}/{product-repo}/products/{product}/`
If the slug is not in the table, halt and ask the user which product/repo this ticket belongs to before proceeding.

## Inputs

- **Product** (required) — key identifying the Manhattan product (e.g. `pos`, `mascp`, `oms`, `sif`, `matm`, `wms`). Resolved via the static table below.
- **Master index** (required) — located at `{product-dir}/influencing-factors.md`. If not found there, ask the user for the full path. A prioritized catalog `Priority | Influencing Factor | Lens | Representative values` under P0/P1/P2 headings, with a trailing `MACHINE_BLOCK`.
- **Plan path** (required) — full local path to the plan file, supplied by the user. Read *Impacted components/files*, *Code changes*, *Design decisions*, *Risks/edge cases*, *Test Plan/Regression*.
- **Module index** (optional) — `{product-dir}/module-index.md`, path↔area resolution (evidence only).

## Output files

Two files written to `{workspace}/{product-repo}/products/{product}/test/<jira_id>/` — resolved via the static table below.

| Product | Active repo | Output path |
|---|---|---|
| `pos` | `active-store` | `{workspace}/active-store/products/pos/test/<jira_id>/` |
| `sif` | `active-store` | `{workspace}/active-store/products/sif/test/<jira_id>/` |
| `mascp` | `active-planning` | `{workspace}/active-planning/products/mascp/test/<jira_id>/` |
| `matm` | `active-transportation` | `{workspace}/active-transportation/products/matm/test/<jira_id>/` |
| `oms` | `active-order` | `{workspace}/active-order/products/oms/test/<jira_id>/` |
| `wms` | `active-warehouse` | `{workspace}/active-warehouse/products/wms/test/<jira_id>/` |

Full resolution protocol and hard enforcement rules: [references/resolve-inputs.md](./references/resolve-inputs.md).

| File | Purpose |
|---|---|
| `<jira_id>_influencing-factors.md` | Human-readable artifact — features, factor table, gaps note. No JSON block. |
| `<jira_id>_influencing-factors.json` | Machine-readable artifact for downstream stages. |

## PICT vs SEED placement rules

| Placement | Meaning | Output location |
|---|---|---|
| **PICT** | Value drives a distinct code branch that must be crossed with other dimensions | `factors[]` |
| **SEED** | Value is orthogonal; one targeted standalone test | `plan_seeds[]` |
| **SPLIT** | Some values PICT, others SEED | Split across both |
| **ASSERT** | Computed outcome of another factor | `evidence` string of parent factor |

The master index provides a pre-categorized `placement_rule`, `pict_values`, and `seed_values` per group — use these as the **recommended starting point**, not the final answer. Phase 4a always confirms with the user.

## Budget formulas

```
COMBO_EST        = max_values × second_max_values × 1.5   (realistic PICT row estimate)
SEED_COUNT       = number of plan_seeds[] entries
SIDE_SURFACE_COUNT = total standalone tests from OC-8 drops
PRACTICAL_TOTAL  = COMBO_EST + SEED_COUNT + SIDE_SURFACE_COUNT
COMBO_BUDGET     = PRACTICAL_TOTAL   (ceiling for COMBO_EST before optimization loop fires)
```

**Budget status line (display always, even when under budget):**

```
PICT budget: COMBO_EST ≈ N rows  [target: ≤COMBO_BUDGET]
Factors: F  |  Value counts: [list sorted descending]  |  Cartesian: C
Seeds (plan_seeds[]): S  |  Side surfaces: X tests
Practical total: COMBO_EST + S + X ≈ T tests
Status: UNDER BUDGET ✅  or  OVER BUDGET ❌ — optimization required
```

**Inflation guardrail.** `PRACTICAL_TOTAL` should NOT exceed `COMBO_EST × 3`. If it does, these are almost certainly PICT dimensions instead of seeds/assertions — recheck:

| What inflates wrongly | Correct treatment |
|---|---|
| Grant missing / flag disabled / excluded item types | Same-branch negatives → collapse to one representative; add 1–2 seeds |
| UI layouts / assistance mode / cart session modes | Validate on representative paths; not cross-multipliers |
| Logging / observability paths | Assertion seeds on existing action paths, not a PICT factor |
| Button label / UI state assertions | Assertion-only on the relevant scenario; not a dimension |

## Key rules

- Master index = prioritized catalog of *lenses*; derive concrete values from plan + code. Be surgical; values must trace to evidence.
- Never silently drop a P0 group. Carry each factor's priority forward — Stage 2 weights PICT coverage by it.
- Single-value factors → `fixed_context[]`. Derived-outcome factors (rule E8) → assertions, not parameters.
- **Phase order is mandatory: 4a → 4b → 4c → 4d → 5.** Phase 4d runs after every OC drop — never skip it.
- **`pict_constraint` syntax:** always use double-quoted value strings: `IF [FactorA] = "value" THEN [FactorB] <> "other";`
- **Seeds are generic, not hardcoded.** Specify only the 1–3 factors that distinguish the seed from the PICT baseline. Labels describe behavioral intent ("Action hidden when grant absent"), not test IDs or factor values.
- **OC checks run in priority order:** OC-8 → OC-9 → OC-5 → OC-2 → OC-10 → OC-1 → OC-3 → OC-6 → OC-7 → OC-4.
- Never ship factors the user did not confirm. Never edit the master index; only propose new *groups* (with a suggested tier) via `gaps`.

## References

- [references/resolve-inputs.md](./references/resolve-inputs.md) — Phase 0 resolution protocol + mandatory gate.
- [references/split-vs-merge.md](./references/split-vs-merge.md) — split-vs-merge heuristic, default, and 30-second check.
- [references/oc-checks.md](./references/oc-checks.md) — Phase 4b inflation checks (7 surgical checks) + Phase 4c OC optimization loop with all 10 OC check definitions.
- [references/quality-recovery.md](./references/quality-recovery.md) — Phase 4d quality recovery protocol (MG1–MG10, gap rules G1–G5, generic seed protocol, P2 non-functional coverage).
- [references/output-format.md](./references/output-format.md) — artifact layout, JSON schema, field rules, Final Gate.
- [references/derive-ideal-test-count.md](./references/derive-ideal-test-count.md) — `DeriveIdealTestCount` algorithm for `combo_budget` derivation.
- [references/validate-output.py](./references/validate-output.py) — Final Gate validator script; run after Phase 6 emit; exits `0` on ALL PASS.

</supporting-info>
