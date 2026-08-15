---
name: manh-product-test-case-generator
version: 2026-07-30
description: Stage 4 of the plan-driven test generation chain. Given the plan and a single one-liner scenario (or the full set from stage 3), expand each one-liner into a detailed, executable test case with preconditions, task/steps, test data, and assertions. Use after manh-product-test-scenario-combination has produced test-scenarios.md.
---

<what-to-do>

Ask for the plan path first. Derive `<jira_id>` and `<product>` from it silently — fall back to asking only if extraction fails. Auto-probe for the stage-3 scenarios files at the standard location; ask the user only if all three probe locations fail. Then ask for scope (all scenarios vs. a single scenario id). Full resolution protocol, probe order, and mandatory gate: [references/resolve-inputs.md](./references/resolve-inputs.md).

Before generating any test case, audit each scenario's `covers[]` combination ids against the upstream combinations JSON. Flag any combination with contradictory parameter values that cannot co-exist in a single test execution. For each flagged combination, choose to drop the scenario, reseed it with the nearest valid value, or split it into two non-contradictory cases. Present the audit summary and confirm with the user before proceeding. Never write a test case for a scenario whose underlying combinations are unresolvably contradictory. Full contradiction patterns and resolution rules: [references/authoring-rules.md](./references/authoring-rules.md).

Extract reusable context from the plan once into `PLAN_CONTEXT` — feature-flag/config state, personas/roles, entities and key fields, APIs touched, golden/seed data — so every case stays consistent.

For every target scenario emit Preconditions, Task/Steps, Test Data, and Assertions detailed enough that a human or automation-generation skill can execute it without re-reading the plan. Carry traceability (`scenario_id`, `covers[]`, `polarity`, `risk`). A multi-layer scenario stays one case with one assertion per layer — never split into near-identical cases. Behavioral scenarios (those whose `covers[]` contains only `BS*` ids) are authored as targeted scenario tests: derive preconditions directly from the scenario label and plan context; do not apply combination-coverage accounting to them. Detailed authoring rules: [references/authoring-rules.md](./references/authoring-rules.md).

Self-check each case before emitting: preconditions + steps + data suffice to reach every assertion; at least one assertion ties back to the scenario's behavior; Boundary cases assert the exact boundary value; Negative cases assert the exact failure mode; no step depends on undefined data. If a gap requires user input, ask with options and a recommendation before continuing.

Write two files to `<out_dir>`. After writing both files, run the final-gate script to verify structural correctness before proceeding:

```bash
python3 references/scripts/validate_test_cases.py \
    --story-id <jira_id> \
    --out-dir <out_dir> \
    --expected-count <N>   # omit when scope = single scenario
```

Exit code 0 = gate passed; exit code 1 = one or more checks failed (fix and re-run before continuing). Full layout, MACHINE_BLOCK schema, gate-check index, and script usage: [references/output-format.md](./references/output-format.md) and [references/scripts/README.md](./references/scripts/README.md). After the gate passes, confirm the user is ready to proceed to stage 5 (categorizer).

Ask questions one at a time using the `ask_user_question` tool — structured options with a short `header`, a descriptive `question`, and 2–4 `options` each with a `label` and `description`. Mark the recommended option with `"(Recommended)"` appended to its label. Wait for the answer before asking the next question. Never ask open-ended free-text questions without options.

</what-to-do>

<supporting-info>

## Inputs

- **`<plan_path>`** (required) — authority for expected behavior, data shapes, config/feature-flag state, and edge handling. Always ask for this first. Derive `<jira_id>` and `<product>` from this path.
  - **`<scenarios_md_path>`** (required) — stage-3 `<jira_id>_test-scenarios.md`; human-readable one-liners. Never ask upfront — auto-probed in priority order: (1) `{workspace}/{product-repo}/products/{product}/test/<jira_id>/`, (2) same directory as the plan, (3) workspace glob.
- **`<scenarios_json_path>`** (required) — stage-3 `<jira_id>_test-scenarios.json`; parsed for `scenarios[]`. Same auto-probe order; prefer `.json` for MACHINE_BLOCK when both exist.

## Output

Two files written to `{workspace}/{product-repo}/products/{product}/test/<jira_id>/` (resolved via `product-registry.md` — same directory as the stage-3 artifacts):

| File | Purpose |
|---|---|
| `<story_id>_test-cases.md` | Human-readable test cases — no embedded JSON |
| `<story_id>_test-cases.json` | MACHINE_BLOCK only, standalone JSON; authoritative artifact for stage 5 |

## Key rules

- Combination integrity audit is mandatory before generating any test case.
- Keep cases self-contained — do not assume the reader has the plan open.
- This skill writes test specs, not automation code.
- Multi-layer scenarios → one case with one assertion per layer.
- Behavioral scenarios (`covers[]` contains only `BS*` ids) are retained as targeted scenario tests. Never deduplicate them against matrix rows even if their parameter vectors overlap.

## References

- [references/resolve-inputs.md](./references/resolve-inputs.md) — Phase 0 resolution protocol + probe order + mandatory gate
- [references/authoring-rules.md](./references/authoring-rules.md) — contradiction patterns + case-authoring rules + worked examples
- [references/output-format.md](./references/output-format.md) — output layout, MACHINE_BLOCK schema, Final Gate
- [references/scripts/README.md](./references/scripts/README.md) — script index, usage, gate-check table
- [references/scripts/validate_test_cases.py](./references/scripts/validate_test_cases.py) — final-gate validator; run after writing output files

</supporting-info>
