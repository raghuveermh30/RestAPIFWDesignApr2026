---
name: manh-product-test-scenario-combination
version: 2026-07-30
description: Stage 3 of the plan-driven test generation chain. Given the plan and the optimized combinations from stage 2 (which have already been contradiction-audited), generate exactly one concise one-liner test scenario per combination, then optimize the set (merge equivalents, merge observation-layer duplicates, split overloaded combos, add plan-implied scenarios combinatorics can't express). Output is one-line scenarios only. Use after manh-product-test-combination-generator has produced test-combinations.md.
---

<what-to-do>

Ask for the change planner path first. Derive `<jira_id>` from it, auto-locate the stage-2 combinations file, and fall back to asking if not found. Full resolution protocol and mandatory gate: [references/resolve-inputs.md](./references/resolve-inputs.md).

For each combination emit exactly one one-liner in the form `Verify <expected behavior> when <factor=value, ...>`. Lead with the behavior, not the data. Include only distinctive factor values. Mark polarity `[Positive] | [Negative] | [Boundary]` and preserve the originating `combination_id`. Behavioral scenarios (those in `behavioral_scenarios[]`) are targeted scenario tests — derive their one-liner directly from the scenario label and plan context; do not apply pairwise-combination accounting to them.

Optimize the set: merge equivalents; merge observation-layer duplicates into one scenario naming both points with the union of `covers[]`; split overloaded combos; add plan-implied scenarios (`source: plan-derived`); prune duplicates and unreachable rows (record under `dropped[]`); order Positive → Negative → Boundary. Full rules and worked example: [references/optimize-rules.md](./references/optimize-rules.md).

Write two files to `<out_dir>` = `{workspace}/{product-repo}/products/{product}/test/<jira_id>/` (resolved via `product-registry.md` — same directory as the stage-2 artifacts). See layout, JSON schema, and final gate in [references/output-format.md](./references/output-format.md).

Ask questions one at a time using the `ask_user_question` tool — structured options with a short `header`, a descriptive `question`, and 2–4 `options` each with a `label` and `description`. Mark the recommended option with `"(Recommended)"` appended to its label. Wait for the answer before asking the next question.

</what-to-do>

<supporting-info>

## Inputs

- **`<plan_path>`** (required) — the change planner file; used to derive `<jira_id>`, feature intent, and domain vocabulary. Always ask for this first.
- **`<combinations_path>`** (required) — stage-2 `*-test-combinations.json`; auto-located at `{workspace}/{product-repo}/products/{product}/test/<jira_id>/<jira_id>-test-combinations.json` once `<jira_id>` and product are resolved. Ask the user only if not found at that path.

## Output

Two files written to `{workspace}/{product-repo}/products/{product}/test/<jira_id>/` (resolved via `product-registry.md` — same directory as the stage-2 artifacts):

| File | Purpose |
|---|---|
| `<jira_id>_test-scenarios.md` | Scenario table — no JSON block |
| `<jira_id>_test-scenarios.json` | Machine-readable structured data for stage 4 |

## Key rules

- Keep one-liners atomic — one verifiable behavior each. Steps, data, and assertions are stage 4's job.
- Merge same-guarantee/different-layer one-liners into one scenario; keep separate only when layers assert different guarantees.
- Behavioral scenarios (those in `behavioral_scenarios[]`) are always retained as targeted scenario tests. Never deduplicate them against matrix-derived scenarios even if their parameter vectors overlap.

## References

- [references/resolve-inputs.md](./references/resolve-inputs.md) — Phase 0 resolution protocol (incl. 3-file disambiguation) + mandatory gate
- [references/optimize-rules.md](./references/optimize-rules.md) — scenario optimization rules + worked examples (merge, split, prune, plan-derived)
- [references/output-format.md](./references/output-format.md) — output layout, MACHINE_BLOCK schema, Final Gate

</supporting-info>
