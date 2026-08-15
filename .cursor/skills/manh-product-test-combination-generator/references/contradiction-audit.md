# Phase 4b — Combination contradiction audit

Run this audit **after Phase 4 optimization and before Phase 5 budget gate**. Check every surviving combination row for contradictory parameter sets — values that cannot logically co-exist in a single test execution. Resolve all contradictions before counting against `combo_budget`; rows that would be dropped here must not consume budget.

## Contradiction patterns

Apply each pattern to every combination. A combination is contradictory if it matches one or more patterns.

| # | Pattern | Detection rule | Disposition |
|---|---------|---------------|-------------|
| P1 | **Guard-path + inapplicable outcome** | A guard-triggered state (e.g. feature flag disabled, grant absent) blocks the primary flow entirely, yet the same combination specifies an outcome that requires the guarded action to execute (e.g. apply-commit failure, internal-void payload, popup-interaction cancel state). The guarded path makes the action permanently unreachable. | **Drop** if no valid precondition set exists. If the network state is the contradiction axis, drop outright — do not reseed, because changing the Error/Info Path field alone does not resolve the gating axis contradiction. |
| P2 | **Prerequisite absent + behavior requiring it** | An apply action or cancel state requires a prior committed state (e.g. "Replace committed", "Deselect committed", "Cancel after internal-void staged") but the same combination declares that prior state absent (e.g. "No previously committed children"). The behavior is impossible to observe without the prerequisite. | **Reseed**: change the prerequisite field to the value the behavior requires. |
| P3 | **Apply disabled + active interaction state** | "No net change (Apply disabled)" or "Apply button disabled" is combined with a cancel state that describes an active user interaction (e.g. "Cancel after selections made but not committed", "Cancel after internal-void items staged"). Apply-disabled means no interaction has produced a committed change — staged states are logically excluded. | **Reseed**: replace the active cancel state with its neutral equivalent (e.g. "Cancel from fresh/initial state (nothing selected)"). |
| P4 | **Wrong-phase error** | A failure mode attributed to one execution phase (e.g. "Server timeout during open / data fetch phase", "API error on popup open") is modeled using a different phase's error parameter value (e.g. "API error on apply commit"). The error cannot manifest in the phase the test expects. | **Reseed**: change the Error/Info Path field to the correct phase's error value (e.g. "API error on popup open (data fetch phase)"). Do not reuse apply-commit error values for open-phase failures. |
| P5 | **Apply-commit network state + no apply-commit error** | Network State is "Apply-commit failure" but Error/Info Path is "No error (happy path)" **and** Apply Action Type is an active action (not "No net change / Apply disabled"). The network state implies the apply will fail — the happy-path outcome contradicts the network condition. | **Reseed**: change Error/Info Path to the apply-commit error value (e.g. "API error on apply commit - no orphan lines"). Exception: when Apply Action Type is "No net change (Apply disabled)", no apply fires — "No error" is correct and is **not** a contradiction. |

## Post-reseed duplicate check

After all reseeds, scan surviving rows for exact duplicates (all parameter values identical). Drop duplicates, keeping the row with the lower original id. Record each drop with the id it duplicates.

## Audit report format

Emit a summary table before advancing to Phase 5:

```
| ID   | Pattern | Action   | Field(s) changed | Note |
|------|---------|----------|-----------------|------|
| C004 | P1      | Dropped  | —               | Grant absent + apply-commit failure; popup unreachable |
| C013 | P2      | Reseeded | Prior Committed → One or more committed | Replace committed requires prior committed children |
| C027 | P3      | Reseeded | Cancel Prior State → Cancel fresh | Apply disabled contradicts staged internal-void cancel |
| C071 | P4      | Reseeded | Error/Info → API error on popup open (data fetch phase) | Open-phase timeout must use open-phase error value |
| C012 | P5      | Reseeded | Error/Info → API error on apply commit - no orphan lines | Apply-commit network state with active apply requires apply-commit error |
| C008 | (dup)   | Dropped  | —               | Duplicate of C018 after reseed |
```

Dropped rows must be recorded in the `dropped[]` array of the output JSON with their contradiction pattern and rationale.
