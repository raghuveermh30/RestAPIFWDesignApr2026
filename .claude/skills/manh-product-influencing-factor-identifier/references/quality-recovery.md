# Phase 4d — Quality recovery check

Every OC drop that removes a factor or collapses values trades PICT coverage for a seed or assertion. This phase verifies the trade was complete — no coverage was silently abandoned. It also checks all mandatory coverage gaps (MG1–MG10) from the master index regardless of OC drops.

This phase is **mandatory** — run after every OC drop, before Phase 5. Cannot be skipped even when under budget.

---

## Step 1 — Build the coverage debt ledger

For each OC drop recorded in this session, list:
- What behavioral dimension was removed or collapsed
- What the tester now cannot verify from the PICT combinations alone
- Whether a seed or assertion was produced to fill the gap

## Step 1b — Check mandatory coverage gaps (MG1–MG10)

For each MG rule in the master index `gap_rules.mandatory_coverage_gaps`, evaluate the trigger against the plan. If the trigger fires and no seed already covers it, add the required seed(s) to `plan_seeds[]`. This check is independent of OC drops — run it even when no OC drops occurred.

| MG | Trigger condition | Required seed(s) |
|---|---|---|
| MG1 | Plan has any cart raindrop / popup / item sub-action | Omni Cart seed + Quote Order seed |
| MG2 | Plan has a commit action sending data to backend API | Server timeout on commit — no orphan lines |
| MG3 | Plan introduces a popup/dialog with long-running API on open | UI timeout — graceful dismiss, no side-effects |
| MG4 | Plan has logging section OR mentions cart session summary | Combined perf + logging + cart session summary seed (one seed, highest-complexity apply path) |
| MG5 | Plan introduces a sub-action requiring elevated permission | Manager approval granted seed + approval denied seed |
| MG6 | Plan has item void / Remove on child line AND mentions Suspend/Resume | Item void + Suspend/Resume seed |
| MG7 | Plan introduces a new DCI or distinct child-line action | User Exit seed for that DCI |
| MG8 | Plan has an add-on/fresh-selection variant AND mentions offline mode | Add-on reprompt offline happy path seed |
| MG9 | Plan introduces a popup AND mentions Suspend/Resume | Suspend while dialog open → resume seed |
| MG10 | Plan mentions cart session summary OR has a cancel path | Cancel path produces no cart session summary event seed |

---

## Step 2 — Generic seed protocol

For every gap in the ledger that has no seed yet, produce a `plan_seeds[]` entry:

- **`label`** — written as a behavioral assertion in the present tense, not a test-case title. State *what* the tester verifies, not *how*. Do not embed factor names or values. Example: `"Action hidden when feature flag disabled"`, not `"FF=off test"` or `"C029"`.
- **`note`** — one sentence: which OC check dropped the coverage + the behavioral reason it must be verified standalone. Example: `"OC-8: orthogonal to popup flow; verify action visible/hidden in isolation from all reprompt-flow factors."`.
- **`values`** — **sparse**: specify only the 1–3 factors that make this seed *distinct from the PICT baseline*. Omit every factor whose default positive value is correct for this scenario. Stage 2 fills unspecified factors automatically. Never enumerate all factors in a seed — that defeats the purpose and hardcodes the model.

---

## Step 3 — Gap-rule seed protocol

Regardless of OC drops, check each gap rule from the master index against the final `factors[]`. For every gap rule that the plan touches but that PICT alone cannot guarantee:

- **G1** (Initial UI control state on screen open) — if the plan has a dialog/popup with a commit button whose initial state is ambiguous, add one seed: `{label: "Initial state of [button] on [screen] open — verify [enabled/disabled] before any user action", values: {<the factor that controls the button>: <positive value>, <Error / Info Path or equivalent>: <no error value>}}`.
- **G2** (Enabled → disabled reverse transition) — if a control starts enabled and can return to disabled by undoing all changes, add one seed: `{label: "All changes undone — [control] returns to disabled state", values: {<state factor>: <baseline with no net change value>}}`.
- **G3** (Negative-case parity across variants) — if one feature variant has a "no data" negative case, verify equivalent exists for all other variants in `factors[]`. If missing, add a seed per variant.
- **G4** (Side-effect assertion after Cancel) — if a Cancel factor is present, add one seed: `{label: "Cancel from [prior state] — underlying cart/order data byte-for-byte unchanged", values: {<Cancel factor>: <most dangerous prior state value>}}`.
- **G5** (Second-invocation consistency) — if the plan has a commit action, add one seed: `{label: "Immediate re-invoke after successful commit — second invocation reflects just-committed state as new baseline", values: {<primary flow factor>: <positive value>, <state factor>: <post-commit baseline>}}`.

---

## Step 4 — Verify P2 non-functional coverage

For every P2 group that was *excluded* in Phase 3, check whether the plan has a behavioral test obligation for it that cannot be deferred. Common P2 groups that produce mandatory seeds even when excluded from PICT:

- **Logging / Observability** (if plan has an explicit logging section with required fields) → one seed per log path (open, apply, cancel, error). `values`: only the factor that discriminates the path.
- **i18n** (if the feature introduces any user-visible string) → one seed: `{label: "All new user-visible strings present and localized in [secondary locale]", values: {}}` — no factor values needed; locale is a test-data parameter, not a PICT dimension.
- **Cart session summary / analytics** (if plan has explicit session analytics requirements) → one seed: `{label: "Session summary event emitted with correct payload after [action]", values: {<action factor>: <apply value>}}`.
- **Extension points / User Exit** (if plan mentions UserExit or extension hooks) → one seed per extension point.
- **CFD pairing** (if plan mentions CFD or the feature touches cart state visible on CFD) → one seed: `{label: "CFD shows correct state while [action] is in progress", values: {<action factor>: <positive value>}}`.

---

## Step 5 — Display the recovery summary

After completing steps 1–4, show:

```
Phase 4d Quality Recovery:
  Gaps found: N
  Seeds added: M  (labels listed)
  Gap-rule seeds added: K  (G1/G2/G3/G4/G5 labels listed)
  P2 non-functional seeds added: J  (labels listed)
  Remaining uncovered gaps: [none | list with reason why deferred]
```

If any gap is left uncovered, record it in `gaps[]` with tier suggestion. Proceed to Phase 5 only after the summary is displayed.
