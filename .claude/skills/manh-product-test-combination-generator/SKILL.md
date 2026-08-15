---
name: manh-product-test-combination-generator
version: 2026-07-30
description: Stage 2 of the plan-driven test generation chain. Given the plan and the matched influencing factors from stage 1, build a PICT model, run PICT to produce a minimal covering set of combinations, optimize that set against the plan (seed must-cover rows, prune impossible combos, enforce priority + config coverage, collapse guard-invariant factors), then audit every combination for contradictions (P1–P5) and resolve them before the budget gate. If the final count exceeds combo_budget from the influencing-factors JSON (default 70 if absent), run a structured optimization loop with user confirmation before emitting. Use after manh-product-influencing-factor-identifier has produced influencing-factors.md.
---

<what-to-do>

Convert the user-finalized factors into the smallest set of high-value parameter combinations using PICT (Microsoft Pairwise Independent Combinatorial Testing), then tune that set with plan context so the combos that matter are kept and impossible ones are dropped. PICT is the prescribed engine — never silently switch algorithms.

Run phases in order. Phase 0 and Phase 5 are blocking gates. Ask questions one at a time, presenting 2–4 options per question with a recommended choice marked "(Recommended)", waiting for the user's answer before continuing.

**Phase 0 — Resolve inputs.** Derive `{product}` slug and `{git-repo}` from the JIRA key prefix using the table in the `Resolve product-dir` block. If the JIRA prefix is **not in the table**, stop and ask the user which product this ticket belongs to before proceeding. Ask the user for `{workspace}` — the root directory containing the product repos (e.g. `active-store`, `active-order`). Construct `<out_dir>` = `{workspace}/{git-repo}/products/{product}/test/<jira_id>/` (absolute). Locate `<jira_id>_influencing-factors.json` inside `<out_dir>`. Validate the mandatory gate before proceeding.

**Phase 1 — Load + classify.** Parse all `factors[]` from `<jira_id>_influencing-factors.json`. Each factor becomes a PICT parameter with its P0/P1/P2 priority; its `values[]` become the parameter values. Apply only the two mandatory classification steps from [references/pict-playbook.md](./references/pict-playbook.md): flag config factors (`is_config`) and collapse guard-invariant factors. If fewer than 2 parameters result, emit one row per value and jump to Phase 6.

**Phase 2 — Build the PICT model.** Before calling `build_pict_model.py`, run `python3 references/scripts/check_constraint_values.py --factors <factors_path>` (see [references/pict-constraint-value-guard.md](./references/pict-constraint-value-guard.md)). If it exits non-zero, stop, show the mismatches to the user, fix the `pict_constraint` field(s) in the factors JSON in-place, and re-run until it exits 0. Never inline ad-hoc constraint-checking code — always use the script. Then run `references/scripts/build_pict_model.py` with the factors JSON. The script builds the parameter/value table, adds the P0 sub-model at strength 3 when it does not cover all parameters (see sub-model guard in [references/pict-playbook.md](./references/pict-playbook.md)), writes a temp `.pict` file internally, runs `pict` at the requested strength (falling back to strength−1 on failure), and prints raw TSV to stdout. Add plan-derived constraints before running — rules and examples in [references/pict-playbook.md](./references/pict-playbook.md). Do not write any `.pict` file to disk manually.

**Phase 3 — Run PICT.** Handled automatically by `build_pict_model.py`. If the `pict` binary is unavailable the script exits with code 2 and instructs the user to install PICT. Do not write any `.pict.out` file to disk.

**Phase 4 — Optimize against the plan.** Pipe the TSV output into `references/scripts/optimize_combinations.py --factors`. Provide `--seeds-file` (AC/Test Plan rows) and `--prune-file` (impossible combos) when available. The script applies seed → prune → risk-weight → coverage-patch passes and prints optimized combinations JSON. Full optimization rules: [references/pict-playbook.md](./references/pict-playbook.md).

**Phase 4b — Contradiction audit (mandatory, runs before budget gate).** Audit every combination row for contradictory parameter sets — values that cannot logically co-exist in a single test execution. For each flagged combination, drop it, reseed it with the nearest valid value, or split it into two non-contradictory rows. After resolving contradictions, drop exact duplicates. Emit the full audit report (flagged ids, pattern, disposition, rationale) before proceeding. Dropped rows are recorded in `dropped[]`. Full contradiction patterns, resolution rules, and report format: [references/contradiction-audit.md](./references/contradiction-audit.md).

**Phase 5 — Count gate + optimization loop (blocking when count > `combo_budget`).** Read `combo_budget` from the influencing-factors JSON (default `70`). If `final_rows ≤ combo_budget`, skip to Phase 6. Otherwise announce the count and budget, then work through the optimization checks in [references/optimization-checks.md](./references/optimization-checks.md) one at a time. Apply each confirmed mutation via `references/scripts/apply_oc_transforms.py --out <out_dir>/<jira_id>_influencing-factors.json` — never inline ad-hoc transforms. Re-run the full Phase 2–4 pipeline after every confirmed change and pipe the output through `references/scripts/check_coverage.py --factors <factors_path>` to verify value coverage and report the new count before the next check. After all checks, ask the user to confirm or override before proceeding. Only advance to Phase 6 on explicit user confirmation.

**Phase 6 — Emit.** Pipe the confirmed combinations JSON into `references/scripts/emit_outputs.py --factors --out-dir <out_dir>`. The script writes two files: `<jira_id>_test-combinations.md` (optimization summary and combinations table, no JSON block) and `<jira_id>_test-combinations.json` (full machine-readable structured data for stage 3) — both in `<out_dir>` alongside the stage-1 artifacts. Give the user the full paths to both output files. MD layout, JSON schema, and the Final Gate are in [references/output-format.md](./references/output-format.md).

</what-to-do>

<supporting-info>

## Resolve product-dir

Derive `{product}` slug and `{git-repo}` from the JIRA key prefix using the table below.
**If the JIRA prefix is not in this table, ask the user which product the ticket belongs to before constructing any path.**

| Jira key prefix | Product slug | Git repo                | `<out_dir>` (relative to `{workspace}`)                          |
|---|---|---|---|
| `POS`           | `pos`        | `active-store`          | `active-store/products/pos/test/<jira_id>/`           |
| `SIF`           | `sif`        | `active-store`          | `active-store/products/sif/test/<jira_id>/`           |
| `OM`            | `oms`        | `active-order`          | `active-order/products/oms/test/<jira_id>/`           |
| `AI`            | `mascp`      | `active-planning`       | `active-planning/products/mascp/test/<jira_id>/`      |
| `MATM`          | `matm`       | `active-transportation` | `active-transportation/products/matm/test/<jira_id>/` |
| `SUP`           | `wms`        | `active-warehouse`      | `active-warehouse/products/wms/test/<jira_id>/`       |

Ask the user for `{workspace}` — the root directory containing local repo clones (the sibling directory that contains `active-store/`, `active-order/`, etc.). Validate it exists; if not, STOP and re-ask.

Construct: `<out_dir>` = `{workspace}/{git-repo}/products/{product}/test/<jira_id>/` (absolute path).

## Inputs

- **`<jira_id>`** (required) — JIRA ticket id (e.g. `POS-123456`). Derived from the plan file path. Used to locate the stage-1 output directory and derive all file names.
- **`<jira_id>_influencing-factors.json`** — located at `<out_dir>`. Must exist and have `user_finalized: true`. If missing, tell the user to complete stage 1 (`manh-product-influencing-factor-identifier`) first.

## Output files

Two files written to `<out_dir>` (= `{workspace}/{git-repo}/products/{product}/test/<jira_id>/`) alongside the stage-1 artifacts:

| File | Purpose |
|---|---|
| `<jira_id>_test-combinations.md` | Human-readable optimization summary and combinations table. No JSON. |
| `<jira_id>_test-combinations.json` | Machine-readable structured data for stage 3. |
| `<jira_id>_influencing-factors.json` (updated in-place) | Stage-1 file carrying all Phase 5 OC mutations. No separate copy is created. |

No `.pict` or `.pict.out` files are written to disk.

## Key rules

- **`combo_budget` is mandatory:** If `final_rows > combo_budget` after Phase 4, Phase 5 must run — never emit without the optimization loop and explicit user confirmation.
- **Recommend before acting:** Every optimization check must present a recommendation with rationale and projected count before applying any change. Never silently drop or collapse a factor.
- **Re-run after every confirmed change:** Re-run the full Phase 2–4 pipeline immediately after each confirmed OC check so the next decision is grounded in an accurate updated count.
- **User confirmation gates emission:** Even when `final_rows ≤ combo_budget` after the loop, present the count and ask for explicit confirmation before writing files.
- Configuration factors (feature flags, grants, business/store config, platform, network mode, gateway mode) are first-class parameters — never silently fix them to a default. Holding config constant while varying only runtime actions is the most common defect this stage prevents.
- Constraints are the highest-leverage input — invest effort there, not row count. PICT keeps the set near-minimal; resist full-cartesian rows.
- Collapse a factor only when the plan explicitly proves behavior does not branch on it; when in doubt, keep it and use OC checks to surface the question.

## References

| Reference | Purpose |
|---|---|
| [references/resolve-inputs.md](./references/resolve-inputs.md) | Phase 0 resolution steps and mandatory gate |
| [references/pict-playbook.md](./references/pict-playbook.md) | Config flagging, guard-invariant collapse, PICT model/constraints/strength, optimization rules, worked example |
| [references/pict-constraint-value-guard.md](./references/pict-constraint-value-guard.md) | Phase 2 pre-flight: detect and fix `pict_constraint` value tokens that don't exactly match `factors[].values[]` (silent PICT mismatch bug) |
| [references/contradiction-audit.md](./references/contradiction-audit.md) | P1–P5 contradiction patterns, resolution rules, and audit report format for Phase 4b |
| [references/optimization-checks.md](./references/optimization-checks.md) | OC-1 through OC-10 optimization checks for Phase 5 |
| [references/output-format.md](./references/output-format.md) | Output layout, JSON schema, Final Gate |
| [references/scripts/README.md](./references/scripts/README.md) | Script index, typical pipeline, optional input file formats |
| [references/scripts/apply_oc_transforms.py](./references/scripts/apply_oc_transforms.py) | Phase 5: apply OC mutations to factors JSON; supports `--run-pipeline` for immediate count reporting |
| [references/scripts/build_pict_model.py](./references/scripts/build_pict_model.py) | Phase 2/3: build PICT model and run pict → TSV |
| [references/scripts/optimize_combinations.py](./references/scripts/optimize_combinations.py) | Phase 4: seed, prune, risk-weight, coverage-patch → combinations JSON |
| [references/scripts/check_coverage.py](./references/scripts/check_coverage.py) | Phase 4/5: verify every factor value appears in ≥1 combination; exits 1 on MISSING |
| [references/scripts/emit_outputs.py](./references/scripts/emit_outputs.py) | Phase 6: write `.md` and `.json` output files |
| [references/scripts/run_pipeline.py](./references/scripts/run_pipeline.py) | Phases 2–6 in one command; alternative to the three-script pipe |

</supporting-info>
