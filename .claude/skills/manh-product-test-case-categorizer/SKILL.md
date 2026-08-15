---
name: manh-product-test-case-categorizer
version: 2026-07-30
description: Stage 5 (final) of the plan-driven test generation chain. Given the plan and the full set of detailed test cases from stage 4, remove redundant cases and add a `category` attribute (test level Unit / Component / Integration / E2E) to each remaining case. Output is the same shape as the stage-4 file — full preconditions/steps/test data/assertions per case — with only the new `category` field added. Use after manh-product-test-case-generator has produced test-cases.md.
---

<what-to-do>

Ask for the plan path first using `ask_user_question`. Derive `<jira_id>`, `<product>`, and `<out_dir>` from it silently — fall back to asking only if extraction fails. Auto-probe for the stage-4 test-cases files and `test-analysis.md` at standard locations; ask the user only if all probe locations fail. Full resolution protocol, probe order, and mandatory gate: [references/resolve-inputs.md](./references/resolve-inputs.md).

Parse `test_cases[]`, retaining the complete object for every case — id, scenario_id, covers[], polarity, risk, preconditions[], steps[], test_data, assertions[]. Do not reduce a case to metadata. Load the `test-analysis.md` taxonomy if present; otherwise use the default taxonomy from the categorize rules.

Remove redundant cases in a coverage-preserving pass. Apply exact/subsumed dedup, same-behavior data-delta merging, and instrumentation/observation-layer merging (merge into one case asserting at both layers). Run the coverage safety check: never drop the only case covering a high-risk pair, Negative, Boundary, or P0 value — merge distinct assertions into the survivor instead. A Boundary or Negative polarity tag is not by itself a shield against dedup. Full rules: [references/dedup-rules.md](./references/dedup-rules.md).

For each surviving case assign the lowest sufficient test level — Unit, Component, Integration, or E2E — as the `category` attribute. Add a `repo_name` field to every case identifying which repo the test belongs to (e.g. `active-store`, `active-order`). For cases categorized as E2E, after assigning the level ask the user via `ask_user_question` which repo(s) the E2E test should target — present the product's known repos as options with the primary product repo pre-selected as the recommendation. Record the answer as `repo_name` on the case. For Unit, Component, and Integration cases, automatically determine `repo_name` by scanning the case's file paths, service names, and module references against the plan's codebase exploration section — do not ask the user. Fall back to the primary product repo only when no signal resolves, flagging with `repo_name_inferred: true`. Taxonomy, level-split, tagging, and repo_name rules: [references/categorize-rules.md](./references/categorize-rules.md).

Write to `<out_dir>/<story_id>-test-cases.md`, overwriting the stage-4 file in place if it already exists, else creating it new. Do not produce a separate `*-test-cases-final.md`. After the file is written, update the input plan file (`<plan_path>`) by appending a `### Generated test artifacts` subsection to its test surface section, recording the full path of the categorized output file. Layout, MACHINE_BLOCK schema, plan write-back format, Final Gate, and worked example: [references/output-format.md](./references/output-format.md).

Ask ALL questions using the `ask_user_question` tool — structured options with a short `header` (≤30 chars), a descriptive `question`, and 2–4 `options` each with a `label` and `description`. Mark the recommended option with `"(Recommended)"` appended to its label. Ask one question at a time and wait for the answer before asking the next. Never ask questions as plain text. Full per-step question specs: [references/resolve-inputs.md](./references/resolve-inputs.md).

</what-to-do>

<supporting-info>

## Inputs

- **`<plan_path>`** (required) — authority for risk, regression callouts, and protected behaviors. Always ask for this first. Derive `<jira_id>` and `<product>` from this path.
- **`<test_cases_path>`** (required) — stage-4 `<jira_id>_test-cases.md` and `<jira_id>_test-cases.json`; parsed for `test_cases[]` via MACHINE_BLOCK. Never ask upfront — auto-probed at `{workspace}/{product-repo}/products/{product}/test/<jira_id>/`, then same directory as the plan, then workspace glob.
- **`test-analysis.md`** (optional) — repo frameworks/conventions and where each test level lives. Never ask the user — auto-probed silently at `.manh-ai-harness/test-analysis.md` in the repo root, then the plan directory. If absent (the expected case for product-level work), use the default taxonomy silently.

## Output

`<out_dir>/<jira_id>-test-cases.md` (where `<out_dir>` = `{workspace}/{product-repo}/products/{product}/test/<jira_id>/`, resolved via `product-registry.md`) — overwrites the stage-4 file in place if it exists, else created new. The categorized set becomes the canonical `<jira_id>-test-cases.md`. No separate `*-test-cases-final.md` is produced.

## Key rules

- Output mirrors the stage-4 format exactly; the only added fields are `category` and `repo_name`. Never strip cases to a metadata-only table.
- Redundancy removal is coverage-preserving: merge distinct assertions into the survivor rather than dropping coverage.
- Prefer pushing cases down the pyramid (cheaper, faster, more stable) without losing the behavior an assertion checks.
- Every case must carry a `repo_name`. For E2E cases, ask the user via `ask_user_question`; for Unit/Component/Integration, determine automatically from file paths and service names in the case content — never ask the user.

## References

- [references/resolve-inputs.md](./references/resolve-inputs.md) — resolution protocol + mandatory gate
- [references/dedup-rules.md](./references/dedup-rules.md) — redundancy-removal rules
- [references/categorize-rules.md](./references/categorize-rules.md) — test-level taxonomy + assignment rules
- [references/output-format.md](./references/output-format.md) — overwrite-or-create logic, output layout, MACHINE_BLOCK schema, Final Gate, worked example

</supporting-info>
