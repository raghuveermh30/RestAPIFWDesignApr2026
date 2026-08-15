# Skill Classification & Lookup Table

Used by `manh-regenerate-skill` to classify each chosen skill and determine the correct
execution path: **meta-skill** (copy + regenerate) or **reference/utility skill** (copy only).

---

## Skill Type Definitions

| Type | Description | Execution path |
|---|---|---|
| **Meta-skill** | Reads harness artifacts from a target repo and generates a repo-specific skill (e.g. `[repo]-change-planner`) | Copy meta-skill → `setup.sh` (pass 1) → execute meta-skill to generate repo-specific skill → `setup.sh` (pass 2) |
| **Reference / Utility skill** | Copied as-is; not executed; no repo-specific output generated | Copy skill → `setup.sh` (single pass) |

---

## Classification Table

| Skill name | Type | Generates |
|---|---|---|
| `manh-change-planner` | Meta-skill | `[repo]-change-planner` |
| `manh-code-reviewer` | Meta-skill | `[repo]-code-reviewer` |
| `manh-code-reviewer-ci` | Meta-skill | `[repo]-code-reviewer-ci` |
| `manh-change-insights` | Meta-skill | `[repo]-change-insights` / `[repo]-commit-analyzer` |
| `manh-backend-test-generator` | Meta-skill | `[repo]-test-generator` |
| `manh-test-generator` | Meta-skill (dispatcher) | `[repo]-test-generator` |
| `manh-test-scenario-identifier` | Meta-skill | `[repo]-test-scenarios` |
| `manh-mapickle-skill-generator` | Meta-skill | `[repo]-mapickle-generator` |
| `manh-feature-flags` | Meta-skill | `[repo]-feature-flags` |
| `manh-logging-generator` | Meta-skill | `[repo]-logging` |
| `ui-perf-analyzer` | Meta-skill | `[repo]-perf-analyzer` |
| `manh-entity-fw` | Reference skill | — |
| `manh-awpf` | Reference skill | — |
| `manh-regression-prevention` | Reference skill | — |
| `manh-jira` | Reference skill | — |
| `manh-add-learning` | Reference skill | — |
| `manh-skill-feedback` | Reference skill | — |
| `manh-ff-cleanup` | Utility skill | — |
| `manh-static-checker` | Reference skill | — |
| `manh-harness-primer` | Reference skill | — |
| `manh-repo-analyzer` | Reference skill (dispatcher) | — |
| `manh-backend-repo-analyzer` | Reference skill | — |
| `manh-frontend-repo-analyzer` | Reference skill | — |
| `manh-mup-repo-analyzer` | Reference skill | — |
| `manh-test-analyzer` | Reference skill (dispatcher) | — |
| `manh-backend-test-analyzer` | Reference skill | — |
| `manh-context-creator` | Reference skill | — |
| `manh-skill-generator` | Reference skill | — |
| `manh-domain-classifier` | Reference skill | — |
| `manh-threat-analyzer` | Reference skill | — |
| `manh-product-doc-analyzer` | Reference skill | — |
| `manh-product-context-creator` | Reference skill | — |
| `manh-product-change-planner` | Reference skill | — |
| `manh-product-code-reviewer` | Reference skill | — |
| `manh-component-graph` | Reference skill | — |
| `manh-bridge` | Utility skill | — |
| `manh-session-reviewer` | Reference skill | — |

> **If a skill is not in this table** (e.g. a newly added skill): check whether its `SKILL.md`
> frontmatter contains a `disable-model-invocation: true` field — if so, treat as reference/utility.
> Otherwise, ask the engineer: "Does `{skill-name}` generate a repo-specific skill? (yes/no)"

---

## Required Artifacts per Meta-skill

Before executing a meta-skill, verify these artifacts exist in `{target-repo}/.manh-ai-harness/`.
Missing REQUIRED artifacts → abort that meta-skill with an error message (see EXECUTION-PROTOCOL.md).
Missing OPTIONAL artifacts → warn and continue.

| Meta-skill | Required artifacts | Optional artifacts |
|---|---|---|
| `manh-change-planner` | `repo-analysis.md`, `entity-analysis-report.md`, `test-analysis.md` | `context-profile.md`, `key-learnings.md` |
| `manh-code-reviewer` | `repo-analysis.md`, `entity-analysis-report.md`, `test-analysis.md`, `key-learnings.md` | `review-checklist.md` |
| `manh-code-reviewer-ci` | `repo-analysis.md`, `entity-analysis-report.md`, `test-analysis.md`, `key-learnings.md` | — |
| `manh-change-insights` | `repo-analysis.md`, `context-profile.md` | — |
| `manh-backend-test-generator` | `test-analysis.md`, `repo-analysis.md`, `context-profile.md` | `entity-analysis-report.md` |
| `manh-test-generator` | `test-analysis.md`, `repo-analysis.md`, `context-profile.md` | — |
| `manh-test-scenario-identifier` | `test-analysis.md`, `repo-analysis.md`, `context-profile.md` | — |
| `manh-mapickle-skill-generator` | `test-analysis.md`, `repo-analysis.md`, `entity-analysis-report.md` | — |
| `manh-feature-flags` | `feature-flags.xml` (from `../configuration` repo) | — |
| `manh-logging-generator` | `repo-analysis.md`, `context-profile.md`, `key-learnings.md` | — |
| `ui-perf-analyzer` | `repo-analysis.md` | — |
