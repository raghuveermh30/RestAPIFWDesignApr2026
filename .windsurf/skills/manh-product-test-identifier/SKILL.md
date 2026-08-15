---
name: manh-product-test-identifier
version: 2026-07-30
description: End-to-end orchestrator for the plan-driven test generation chain. Sequences five stages in order — influencing-factor identification → combination generation → scenario combination → test-case expansion → test-case categorization — pausing after each stage for explicit user confirmation before advancing. Single entry point eliminates the need to invoke each sub-skill individually. Use when a plan exists and you need the full test artifact set produced in one guided session.
trigger: manual
---

<what-to-do>

Ask for the plan path first. The plan path is accepted in any of these forms — **MUST** support all of them:

- **A single file path** (e.g. `.../plan/story/2026-q3/JIRA-123-plan.md`) — the plan is that file only.
- **A folder path only** (e.g. `.../plan/story/2026-q3/JIRA-123/`) — the plan is the folder's contents.
- **A folder path plus a file name given together** (e.g. "the plan is in `.../JIRA-123/`, main file `plan.md`", or a path like `.../JIRA-123/plan.md` where sibling files in the same folder are also relevant) — treat this the same as the folder case: resolve the folder, and still pull in every eligible file in it (the named file is simply one of them, not the sole plan).

**MUST**: whenever a directory is in play (folder-only, or folder+file-name given together), the plan is the directory's contents — treat EVERY file inside it as part of the plan, EXCEPT any file whose name starts with a dot (`.`). Dotfiles and dotdirectories (e.g. `.git`, `.manh-ai-harness`, `.DS_Store`) are ALWAYS excluded, without exception, and this exclusion is never negotiable or user-overridable. This directory-as-plan rule is stringent and must be applied every time a folder is involved — do not silently pick "the main file" or ask the user to narrow it down; concatenate/consider all non-dot files as the combined plan content. State explicitly to the user which files were included and which (if any) dotfiles were skipped before proceeding. From the resolved plan (single file or directory), silently derive `<jira_id>`, `<product>`, and `<out_dir>` using the JIRA-prefix table in the supporting-info section. Ask with options if any cannot be extracted. Once resolved, confirm the context block with the user before starting Stage 1:

```
Plan:       <plan_path>
JIRA ID:    <jira_id>
Product:    <product>
Output dir: <out_dir>
```

Carry these four values through every stage. Never re-ask for them unless a stage reports a resolution failure.

Run the five stages in order. After each stage completes, surface a brief summary of what was written, then ask the user — one question at a time — whether to proceed, revise, or stop. Only advance on explicit confirmation. At any "stop here" choice, print the latest artifact path so the user can resume with the individual sub-skill later.

**Stage 1 — Influencing Factor Identification** (`manh-product-influencing-factor-identifier`). Execute that skill's full protocol (Phases 0–6) with the already-resolved inputs — skip any Phase 0 steps that re-ask for what was collected at startup. When done, surface the factor table and the two output files. Ask: ready to generate combinations, revise factors (re-run Phase 5), or stop?

**Stage 2 — Combination Generation** (`manh-product-test-combination-generator`). Execute that skill's full protocol (Phases 0–6). When done, surface the budget status line and final combination count. Ask: proceed to scenarios, re-run OC optimization, or stop?

**Stage 3 — Scenario One-Liner Generation** (`manh-product-test-scenario-combination`). Execute that skill's full protocol. When done, surface scenario count by polarity and any plan-derived additions. Ask: expand all scenarios, expand a subset (user names the IDs), or stop?

**Stage 4 — Test Case Expansion** (`manh-product-test-case-generator`). Execute that skill's full protocol using the scope from Stage 3. When done, surface total cases written and the `validate_test_cases.py` gate result. Ask: proceed to categorization, review the test-cases file first, or stop?

**Stage 5 — Test Case Categorization** (`manh-product-test-case-categorizer`). Execute that skill's full protocol. When done, surface dedup removals, category distribution (Unit / Component / Integration / E2E), and confirmation that the plan file was updated with the `### Generated test artifacts` write-back. No further gate — print the session summary:

```
Test generation chain complete for <jira_id>

Artifacts written to: <out_dir>

  Stage 1 — Influencing factors : <jira_id>_influencing-factors.md / .json
  Stage 2 — Combinations        : <jira_id>_test-combinations.md / .json
  Stage 3 — Scenarios           : <jira_id>_test-scenarios.md / .json
  Stage 4 — Test cases (raw)    : <jira_id>_test-cases.json
  Stage 5 — Test cases (final)  : <jira_id>_test-cases.md  ← canonical output

Category distribution: Unit=X  Component=Y  Integration=Z  E2E=W  Total=T
```

Ask questions one at a time using `ask_user_question` — structured options with a short header, descriptive question, and 2–4 options each with a label and description. Mark the recommended option with "(Recommended)" appended to its label. Wait for the answer before asking the next question. Never skip a gate. Never re-implement sub-skill logic — delegate in full to each skill's SKILL.md. If a sub-skill's final-gate script exits non-zero, stop, show the error, fix it, and re-run before advancing.

</what-to-do>

<supporting-info>

## Resolve product-dir

Identify `{product}` slug from the plan path or JIRA key prefix using the table below. Ask once if not inferable.
Construct `<out_dir>` directly from the Product column below (already anchored to `<workspace>`, the top-level directory containing all product repos).

| Product | Output directory |
|---|---|
| `pos` | `<workspace>/active-store/products/pos/test/<jira_id>/` |
| `sif` | `<workspace>/active-store/products/sif/test/<jira_id>/` |
| `mascp` | `<workspace>/active-planning/products/mascp/test/<jira_id>/` |
| `matm` | `<workspace>/active-transportation/products/matm/test/<jira_id>/` |
| `oms` | `<workspace>/active-order/products/oms/test/<jira_id>/` |
| `wms` | `<workspace>/active-warehouse/products/wms/test/<jira_id>/` |

| JIRA prefix | Product |
|---|---|
| `POS` | `pos` |
| `SIF` | `sif` |
| `OM` | `oms` |
| `AI` | `mascp` |
| `MATM` | `matm` |
| `SUP` | `wms` |

## Stage chain

| Stage | Sub-skill | Reads | Writes |
|---|---|---|---|
| 1 | `manh-product-influencing-factor-identifier` | plan + master index | `<jira_id>_influencing-factors.md` / `.json` |
| 2 | `manh-product-test-combination-generator` | `influencing-factors.json` | `<jira_id>_test-combinations.md` / `.json` |
| 3 | `manh-product-test-scenario-combination` | `test-combinations.json` | `<jira_id>_test-scenarios.md` / `.json` |
| 4 | `manh-product-test-case-generator` | `test-scenarios.json` | `<jira_id>_test-cases.md` / `.json` |
| 5 | `manh-product-test-case-categorizer` | `test-cases.json` | `<jira_id>_test-cases.md` (overwrite) |

Each sub-skill's `references/` directory is authoritative for that stage. This skill defers to them at execution time and does not duplicate their rules.

</supporting-info>
