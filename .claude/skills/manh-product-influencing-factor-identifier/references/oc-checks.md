# Phase 4b — Inflation checks (7 surgical checks)

Run these seven checks **in order** after Phase 4a. Each asks: "does this factor or value actually branch the code under test, or is it a duplicate / assertion / impossible combo?"

**Auto-apply the recommended resolution for each check** — do not ask the user. Record every decision (kept, collapsed, dropped, or constrained) in `dropped_factors[]` or in the factor's `evidence` string. After completing all seven checks, show a brief summary of every decision made before proceeding to Phase 4c.

---

**Check 1 — Multi-value negative-only factors: do all negative values hit the same code branch?**
For every factor whose `negative[]` contains ≥2 values, determine from the plan and code: does each negative value trigger a distinct conditional branch, or do they all evaluate the same guard to `false`?
- If all negatives execute the same branch → **auto-apply: collapse to one representative negative value** (e.g. "Excluded [type]"). Note the original values in `evidence`.
- If each negative has its own branch → keep all values.

**Check 2 — Multi-value context factors: do all non-baseline values follow the same code path?**
For every context factor with ≥3 values, determine from the plan: does each non-baseline context have a distinct code path, or do they all fall through to the same fallback?
- If all non-baseline contexts share the same fallback → **auto-apply: collapse to 2 values**: baseline + one representative fallback. Note individual contexts in `evidence` as required test data seeds.
- If each context has distinct branching → keep all values.
- If each context must be individually covered for regression regardless of branching → keep all values but note as fixed-coverage seeds.

**Check 3 — Crash / force-close factors subsumed by existing failure-phase values:**
For every crash / force-close / app-restart factor, determine: is each value already represented as a failure phase in a failure/error scenario factor already in the model?
- If yes → **auto-apply: drop the factor**; add a sub-case note to the failure scenario factor's `evidence`. Move to `dropped_factors[]`.
- If the crash introduces a genuinely distinct code path not covered by any error value → keep.

**Check 4 — Derived-outcome factors (rule E8):**
For every factor, determine: can this factor's values be set independently by the tester, or are they computed outcomes of other factors already in the model?
- Values that describe UI display state (enabled/disabled, visible/hidden, loading) and are computable from other factors → **auto-apply: drop; add as assertion notes on the parent factor(s).**
- If the factor represents a genuinely independent setup dimension the tester controls → keep.

**Check 5 — Mode-conditional factors: does this factor only apply under one value of another factor?**
For every factor whose values are only meaningful when another factor takes a specific value, determine: are any values logically impossible in certain mode combinations?
- **Auto-apply: add a PICT constraint** pruning the impossible combinations. Store the constraint string in `pict_constraint` using **double-quoted** PICT syntax: `IF [FactorA] = "value" THEN [FactorB] <> "impossible-value";`. Do not remove the values — PICT prunes via constraint.
- If the factor applies equally across all mode values → keep as-is with no constraint.

**Check 6 — Observability / logging factors as PICT parameters:**
For every factor derived from an observability or logging group in the catalog, determine: are the values independent inputs the tester controls, or assertions about what gets logged on action paths already modelled by other factors?
- If every log value maps 1:1 to an action path already covered by another factor → **auto-apply: drop the factor**; add logging assertion notes to the corresponding factors' `evidence`. Move to `dropped_factors[]`.
- If logging behavior differs in a way no other factor captures → keep.

**Check 7 — Cancel / dismiss factors: apply rule E7 value minimisation.**
For every cancel / dismiss factor with ≥3 values, determine: is any value a special case of a failure scenario already in the model rather than a distinct prior-state of the cancel action itself?
- Values that are combinations of an already-modelled failure scenario + cancel action → **auto-apply: drop that value** from the cancel factor and note the combination in `evidence`. Target: 2 values covering "no pending changes" and "pending changes not applied".
- If a third cancel prior-state has genuinely distinct cleanup semantics not covered by any failure scenario → keep.

---

# Phase 4c — Optimization loop (OC checks, priority order)

Run when `COMBO_EST > COMBO_BUDGET`. Run applicable OC checks **in priority order** (highest-savings first: OC-8, OC-9, OC-5, OC-2, OC-10, OC-1, OC-3, OC-6, OC-7, OC-4). For each check:

1. **Evaluate the trigger silently.** If the trigger condition is not present in the current factor set, skip without logging — never surface a check that cannot fire.
2. **If the trigger fires, auto-apply the recommended action** immediately to `factors[]`. Log the decision in this format (inline, no user prompt):

```
OC-N applied: [Check name]
Factor: "[Factor name]" — [one sentence: what was done and why]
COMBO_EST: ~X → ~Y rows
```

3. **Recompute COMBO_EST after each applied check.**
4. If `COMBO_EST ≤ COMBO_BUDGET`: stop the loop, show "Budget reached ✅ (~N rows). Proceeding to Phase 5." and exit.
5. Move to the next applicable check.

After all 10 checks are exhausted, if `COMBO_EST` is still > `COMBO_BUDGET`: auto-accept and proceed to Phase 5 if `COMBO_EST ≤ COMBO_BUDGET × 1.7` (rounded), logging "All OC checks exhausted. Proceeding at ~N rows." If `COMBO_EST > COMBO_BUDGET × 1.7`, surface one `askUser()` call listing the remaining over-budget factors and asking which to remove.

---

## OC check definitions (trigger + action, in priority order)

**OC-8 — Orthogonal feature factor** *(highest savings — removes entire factor)*
- **Trigger:** Any factor that gates a distinct feature whose test surface does not interact with the primary flow factors (i.e. its values do not combine meaningfully with the main flow factors under test).
- **Action:** Remove factor from `factors[]`. Add entry to `dropped_factors[]` with `standalone_rows` guidance (3–5 explicit test rows). Record each row as a **generic seed** in `plan_seeds[]` using the seed protocol in [quality-recovery.md](./quality-recovery.md).

**OC-9 — Gating-only factor with uniform blocked outcome** *(high savings — collapses N negatives to 1)*
- **Trigger:** Any factor where ≥2 negative values all produce exactly the same user-visible blocked outcome (feature suppressed, entry point hidden, action unavailable) with no behavioral difference between them.
- **Action:** Collapse all negative values to one representative (e.g. `"[Gate] disabled or absent"`). Add one **generic seed** per original negative state using the seed protocol in [quality-recovery.md](./quality-recovery.md).

**OC-5 — Mode-conditional impossible values** *(medium savings — adds constraint, prunes combos)*
- **Trigger:** Any factor that contains a value that is logically impossible or meaningless when another factor takes a specific value.
- **Action:** Add `pict_constraint` using double-quoted PICT syntax: `IF [FactorA] = "value" THEN [FactorB] <> "impossible-value";`. Do not remove the value — PICT prunes via constraint. Recompute.

**OC-2 — Context factor with shared fallback** *(medium savings — collapses ≥3 values to 2)*
- **Trigger:** Any context factor with ≥3 values where the plan shows the non-baseline values all fall through to the same code path.
- **Action:** Collapse non-baseline values to one representative. Add the individual contexts to `evidence` as required test data, and add each as a `plan_seeds[]` entry for regression coverage.

**OC-10 — Cross-factor value duplication** *(medium savings — drops entire factor)*
- **Trigger:** Any factor whose boundary or negative value is logically equivalent to a specific value in another factor already in the model.
- **Action:** Drop the lower-priority factor. Add its boundary/negative values as `plan_seeds[]` entries. Move to `dropped_factors[]`.

**OC-1 — Multi-value negative block: same branch** *(low-medium savings — collapses negatives)*
- **Trigger:** Any factor where `negative[]` contains ≥2 values and the plan/code shows no distinct conditional branch per negative (all evaluate the same guard to `false`).
- **Action:** Collapse to one representative negative value. Note originals in `evidence`.

**OC-3 — Crash/force-close factor subsumed by failure scenario** *(low-medium savings — drops factor)*
- **Trigger:** Any crash / force-close factor whose values are already represented as failure phases in a failure/error scenario factor already in the model.
- **Action:** Drop the crash factor. Add sub-case note to the failure scenario factor's `evidence`. Move to `dropped_factors[]`.

**OC-6 — Observability/logging factor: assertion not input** *(low savings — drops factor)*
- **Trigger:** Any factor derived from an observability or logging group in the catalog where every value maps 1:1 to an action path already covered by another factor in the model.
- **Action:** Drop the factor. Add log assertion notes to the mapped factors' `evidence`. Move to `dropped_factors[]`.

**OC-7 — Cancel factor with in-flight value covered by failure scenario** *(low savings — removes 1 value)*
- **Trigger:** Any cancel / dismiss factor with ≥3 values where one value represents "cancel during in-flight request" and the failure scenario factor already includes the corresponding failure value.
- **Action:** Drop the in-flight cancel value. Record it as a combination of the failure scenario value + cancel action in the cancel factor's `evidence`.

**OC-4 — Derived-outcome factor: UI state not tester-controlled** *(variable savings — drops factor)*
- **Trigger:** Any factor whose values describe what the UI shows (enabled/disabled, visible/hidden, loading state) rather than what the tester independently sets up.
- **Action:** Drop the factor. Add values as assertion notes in parent factor(s) `evidence`.
