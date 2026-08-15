# Phase 1b — Combination integrity audit

Before generating any test case, audit every scenario's `covers[]` combination ids. A combination is **contradictory** when two or more of its parameter values cannot logically co-exist in a single test execution — meaning no valid precondition set can satisfy all values simultaneously.

## Contradiction patterns (generic — apply to any domain)

| Pattern | Description | Disposition |
|---|---|---|
| **Info/guard path + inapplicable boundary** | A guard-triggered path (e.g. "no items found", "action blocked", "Apply disabled") is combined with a boundary value that only applies when items exist or the action is enabled (e.g. max-selectable count, upper-bound item count). The boundary is unreachable in that path. | Drop boundary value; reseed with "normal data" or remove the boundary axis entirely from this combination. |
| **Prerequisite absent + behavior requiring it** | A behavior requires a specific prior state (e.g. "previously committed items", "staged selections") but the same combination declares that prior state absent (e.g. "no previously committed children"). The behavior is impossible to observe. | Reseed: change the prerequisite field to the value required by the behavior, or drop the combination if no valid reseeding exists. |
| **Disabled action + active interaction** | "Apply disabled / no net change" is combined with parameters that describe an active, committed user interaction (e.g. "staged internal-void items", "selections made but not committed", "cancel after staging"). An Apply-disabled state means no interaction has produced a change — staged items are logically excluded. | Reseed: replace the active-interaction value with its neutral equivalent (e.g. "cancel from fresh/initial state"), or drop if the interaction is the only meaningful axis. |
| **Error in wrong phase** | A failure mode attributed to one phase (e.g. "open-phase timeout") is modeled using a different phase's error parameter (e.g. "apply-commit failure"). The failure cannot manifest in the phase the test expects. | Reseed: move the failure to the correct phase parameter, or split into two cases (one for each phase's failure). |
| **Mutually exclusive state labels** | Two values in the same combination describe the same dimension with incompatible states (e.g. a reprompt type that implies "no prior selections" while a cancel-prior-state implies "items were staged"). | Identify which axis drives the scenario's observable behavior; set the other axis to its neutral/compatible value. |

## Disposition rules

- **Drop** — use when no valid precondition set exists for the combination and no sibling combination already covers the intended behavior.
- **Reseed** — use when a single value substitution yields a valid, non-duplicate combination. Annotate the reseeded field and value in the scenario's `covers[]` note.
- **Split** — use when the combination conflates two independently meaningful sub-states; emit two cases each with a suffix (e.g. `TC-S031a`, `TC-S031b`).
- Never silently ignore a contradiction — always report the flagged combination id, the contradictory field pair, and the chosen disposition before generating the case.

---

# Phase 2 — generate each test case

For every target scenario emit:

1. **Preconditions** — the system/data/config state that must hold first. Be concrete: feature flag ON/OFF, persona + grants, seeded entities with key field values, environment/ARI if relevant. Reuse `PLAN_CONTEXT`; never leave a precondition implicit.
2. **Task / Steps** — numbered, imperative, one action per step, in execution order. Each step observable. Include the exact UI element / API endpoint / payload when the plan specifies it.
3. **Test Data** — the concrete values for this scenario's factor combination (pull from the originating combination via `covers[]`), plus supporting master data.
4. **Assertions / Expected Results** — one assertion per checkable outcome. Cover the primary behavior AND the guardrails the plan calls out (state written, message shown, no regression to adjacent fields, audit/log entry). Negative scenarios assert the specific error/handling, not just "fails".
   - **Multi-layer scenarios stay one case.** If the scenario names more than one observation point (e.g. "confirmed at both the client request layer and the backend"), emit a single case with **one assertion per layer** — do not split into two near-identical cases.
5. Carry traceability: `scenario_id`, `covers[]` (combination ids), `polarity`, `risk`.

## Behavioral scenario authoring

Scenarios whose `covers[]` contains only `BS*` ids are **behavioral scenarios** — they target a specific observable behavior identified during planning that the pairwise matrix cannot express (e.g. re-invoke sequencing, force-close recovery, platform-specific layout, localization, mid-flow state changes).

Rules for behavioral scenarios:
- **Do not apply pairwise-combination accounting** — their parameter vectors may overlap with matrix rows, but their label names a distinct behavior. Never deduplicate them against matrix-derived scenarios.
- **Derive preconditions from the label and plan context**, not from a combination row. The label is the authoritative specification.
- **Keep them all** — behavioral scenarios represent plan acceptance criteria that combinatorics cannot generate. Dropping one is equivalent to dropping an acceptance criterion.
- Author them at the most appropriate test level for their behavior (often Integration or E2E rather than Unit).

## Worked example — multi-layer assertions (generic)
When a stage-3 scenario names more than one observation point, generate **one** case with **one assertion per layer**:

```
TC-S009 — At most one performOrderReturn on rapid double-click (frontend + backend)
Assertions:
  - [frontend] Zero POST .../performOrderReturn appear in the Network tab during the
    in-flight window; <=1 after processBarcode completes.
  - [backend]  The server API log records at most one performOrderReturn for the whole
    interaction; no duplicate return order is created.
```

This keeps both observation points in a single executable case, so stage 5 has nothing left to merge for this pair.
