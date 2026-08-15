# Optimization Checks (OC-1 through OC-10)

Run each applicable check in order during Phase 5, one question at a time. For every check that fires, state clearly: what the problem is, the recommendation with rationale, and the projected row count after applying the recommendation. Apply confirmed mutations via `apply_oc_transforms.py --out <factors_path>`. Re-run the full Phase 2–4 pipeline after each confirmed change and report the new count before the next check.

---

## OC-1 — Multi-value negative-only factors: same code branch?

**Trigger:** Any factor where `negative[]` contains ≥2 values.

Ask: "Factor **X** has N negative values: [list]. Does the plan or code show a distinct conditional branch per negative value, or do they all evaluate the same guard to `false`?"

- **"Collapse to one representative negative value" (Recommended when same branch)** — all negative values hit the same guard; collapse to one. Note originals in evidence. Re-run pipeline.
- **"Keep all negative values"** — each negative value has its own distinct conditional branch.

---

## OC-2 — Multi-value order/context factors: shared fallback?

**Trigger:** Any Order State / context factor with ≥3 values.

Ask: "Factor **X** has N context values: [list]. Does the plan show a distinct code path per context, or do [non-baseline values] all fall through to the same fallback?"

- **"Collapse non-baseline values to one representative" (Recommended when shared fallback)** — non-baseline values all fall to the same fallback; collapse. Note all contexts in evidence as required test data. Re-run pipeline.
- **"Keep all context values"** — plan shows a distinct code path per context value.
- **"Remove from PICT; add as fixed-coverage seed rows" (Recommended when each context is required for regression)** — keep all values but remove factor from PICT free dimensions; add each as a plan-seed row instead.

---

## OC-3 — Crash / force-close factors: subsumed by Error Scenario?

**Trigger:** Any factor derived from crash/force-close/restart group.

Ask: "Factor **X** has values [list]. Are these already represented as failure phases in **Error Scenario** [list its values]?"

- **"Drop factor; subsume into Error Scenario" (Recommended when all values are covered)** — all crash/force-close values are already represented in Error Scenario. Move to `dropped_factors[]`. Add sub-case note to Error Scenario evidence. Re-run pipeline.
- **"Keep factor"** — this factor contains a genuinely distinct path not represented in Error Scenario.

---

## OC-4 — Derived-outcome factors (rule E8): assertion, not input?

**Trigger:** Any factor whose values describe UI states (enabled/disabled, visible/hidden) or computed results rather than tester-controlled setup.

Ask: "Factor **X** has values [list]. Can these values be set independently by the tester, or are they outcomes computed from other factors already in the model?"

- **"Drop factor; add as assertions on parent factor(s)" (Recommended when values are derived outcomes)** — values are computed from other factors; drop factor and embed them as assertions in parent factor evidence. Re-run pipeline.
- **"Keep factor"** — values represent a genuinely independent tester-controlled setup dimension.

---

## OC-5 — Mode-conditional factors: only meaningful for one mode?

**Trigger:** Any factor that is logically N/A when another factor takes a specific value (e.g. internal void only applies when Reprompt Mode = Optional-linked).

Ask: "Factor **X** only applies when **[other factor]** = **[value]**. Should we constrain it so impossible combinations are eliminated?"

- **"Add PICT constraint to eliminate impossible combinations" (Recommended)** — add `IF [Mode] = "X" THEN [Factor] = "N/A"` and store in `pict_constraint` on the factor. Collapse active values to the minimum covering distinct code paths. Re-run pipeline.
- **"Keep factor unconstrained"** — factor applies meaningfully across all modes.

---

## OC-6 — Log / observability factors: assertion, not input?

**Trigger:** Any factor derived from a Logging / Observability group.

Ask: "Factor **X** has values [list]. Do these values map 1:1 to action paths already covered by other factors ([list mapped factors])?"

- **"Drop factor; add log assertions to corresponding factor evidence" (Recommended when 1:1 mapping)** — logging values map 1:1 to paths covered by other factors; drop and embed as assertions. Re-run pipeline.
- **"Keep factor"** — logging behavior differs in a way that no other factor in the model captures.

---

## OC-7 — Cancel / dismiss factors: rule E7 minimisation.

**Trigger:** Any Cancel / Dismiss factor with ≥3 values.

Ask: "Factor **X** has N cancel prior-state values: [list]. Is **[specific value]** a special case of Error Scenario + cancel action, rather than a distinct prior-state?"

- **"Drop extra value; reduce to 2 prior-state values" (Recommended when value is subsumed by Error Scenario)** — [specific value] is a special case of Error Scenario + cancel; reduce Cancel factor to 'no pending changes' and 'pending changes not applied'. Re-run pipeline.
- **"Keep all cancel values"** — third value has genuinely distinct cleanup semantics not captured elsewhere.

---

## OC-8 — Orthogonal features: factor independent of all reprompt-flow factors?

**Trigger:** Any factor that gates a distinct, separate feature not part of the primary flow (e.g. a child-line action independent of the popup flow).

Ask: "Factor **X** gates **[separate feature name]**. Does it interact with any of the other factors in this model ([list]), or is it fully orthogonal?"

- **"Remove from PICT model; test as standalone" (Recommended when fully orthogonal)** — factor does not interact with any other model factor. Remove from PICT; test [feature] as a standalone parameterized test (3–5 rows). Add a note in the output referencing it. Re-run pipeline.
- **"Keep factor in model"** — factor interacts with one or more other factors in this model.

---

## OC-9 — Gating-only factors: single outcome regardless of other factors?

**Trigger:** Any factor where all non-positive values produce the same outcome as an existing negative-gating factor (e.g. FF=off, Grant=absent).

Ask: "Factor **X** value **[value]** blocks the feature entirely — same outcome as [FF=off / Grant=absent]. Does it produce any distinct popup/apply/cancel/void behavior rows?"

- **"Drop from PICT; add one plan-seed row for the blocked state" (Recommended when no distinct behavior rows)** — factor only produces a single blocked outcome already covered by another gating factor. Re-run pipeline.
- **"Keep factor in model"** — factor produces distinct behavior beyond just blocking the feature.

---

## OC-10 — P2 factors: explicit plan signal present?

**Trigger:** Any P2 factor remaining in the model.

Ask: "Factor **X** (P2: [group]) is in the model. Does the plan explicitly mention [the non-functional concern] — e.g. a plan section on logging, i18n strings, or version skew? Or was it inferred?"

- **"Drop from PICT; it was included speculatively" (Recommended when inferred)** — no explicit plan signal for this non-functional concern; drop from PICT. Re-run pipeline.
- **"Keep factor"** — this non-functional concern is explicitly named in the plan.
