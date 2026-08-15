# Phase 3 — assign test levels (recorded as `category`)

For each surviving case, assign the **lowest sufficient level**. This is the value of the new `category` attribute — the only field this stage adds; every other field is preserved verbatim (subject to Phase-2 merge edits).

- **Unit** — pure logic / a single function or service method, no I/O, easily isolated (discount calculation, formatter, validator branch).
- **Component** — one component/module with collaborators mocked (cart service behavior, a UI component render + interaction).
- **Integration** — two or more modules/APIs interacting, or state/persistence crossing a boundary (order change-log written, cross-module call).
- **E2E** — a user-visible workflow across the running app / multiple services (full checkout, BOPIS flow).

Rules:
1. Pick the cheapest level that can still observe every assertion. If an assertion needs a real cross-module effect, the case is at least Integration.
2. A case may **split by level** if some assertions are unit-checkable and others need E2E — emit `TC-x.u` and `TC-x.e` (each a full case with its own `category`) and note the split.
3. Tag each case with `framework`/`target_path` if `test-analysis.md` provides it.
4. Add `automation_candidate: yes|no` (high-risk + deterministic = yes).
5. Set `category` = the assigned level string (`Unit` | `Component` | `Integration` | `E2E`).
6. Set `repo_name` on every surviving case:
   - **E2E cases**: ask the user via `askUser()` (Step 6 in resolve-inputs.md) — present the primary product repo as the recommended option alongside other repos known for the product.
   - **Unit / Component / Integration cases**: infer `repo_name` automatically from the case content — do NOT ask the user. Use the following inference priority:
     1. **File path signals** — scan `steps[]`, `preconditions[]`, `assertions[]`, and `test_data` for explicit file or module paths (e.g. `libs/services/pos/cart/src/...`, `libs/ui/pos/...`). Match the path prefix against the product's known repo structure from the plan's codebase exploration section (Section 1 of the plan). The owning repo is whichever repo contains that path.
     2. **Service / class name signals** — if no path is found, match service class names or Angular module names mentioned in the case against known services listed in the plan (e.g. `get-linked-item.service.ts` → `active-store`).
     3. **Fallback** — if neither signal resolves to a repo, use the primary product repo (`{product-repo}` from Phase 0 Step 2) and annotate with `repo_name_inferred: true` in the MACHINE_BLOCK so the output is transparently flagged.
   - For level-split cases (`TC-x.u` / `TC-x.e`), infer `repo_name` independently on each split case using the same priority order.
