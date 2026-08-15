# Split-vs-merge heuristic (one value or two?)

Apply whenever two candidate values *might* be the same thing (e.g. `Object.freeze` vs `Immer_sealed`, `Offline` vs `Timeout`, `iPhone` vs `iPad`). Never ask the user "split or merge?" blind — present this rule and default so they answer in one line.

**Rule.** Model two values **separately** only if the code under test **branches on the distinction** — an actual code path, error type, config flag, or assertion that observes *which* of the two it is. If nothing in the exercised code or plan behaves differently between them, **merge**.

**Decision:**
1. Both mechanisms appear in the exercised path AND code branches on which one → **keep separate** (two PICT values).
2. Only one mechanism actually runs (the other is disabled in the test build, e.g. `setAutoFreeze(false)`, or a device never targeted) → **drop the unused one**, model only the real one.
3. Both may occur but no code path observes the difference → **merge** into one value (e.g. one `Frozen` instead of `Object.freeze` + `Immer_sealed`).

**Default when unsure: MERGE.** Splitting on a distinction the code does not observe multiplies that factor's PICT contribution for no added coverage. Keep the coarse, behavior-defining value (`Frozen` vs `Not-frozen`); split back out later only if Stage 5 RCA shows a real miss.

**30-second confirmation hint to offer the user:** grep the impacted module for the two mechanisms (e.g. `produce(` / `from 'immer'` / `setAutoFreeze` vs `Object.freeze(`) and check the test/jest setup for anything that disables one. If one is disabled or absent, the honest answer is merge/drop.
