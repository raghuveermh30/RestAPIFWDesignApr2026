# Generated Skill Template

This is the template for the generated `[repo]-test-orchestrator` skill written to
`.manh-ai-harness/skills/{repoName}-test-orchestrator/SKILL.md`.

Placeholders in `{curly braces}` are substituted at scaffold time using the values
extracted in Steps 0–2 of the meta-skill.

---

## Substitution Reference

| Placeholder | Source |
|---|---|
| `{repoName}` | `context-profile.md` or directory name |
| `{namingConvention}` | `test-analysis.md` F7 |
| `{coverageCommand}` | `test-analysis.md` F2 coverage section |
| `{coverageThreshold}` | `test-analysis.md` F2 coverage gate |
| `{testScenariosSkill}` | `"{repoName}-test-scenarios"` or NOT INSTALLED message |
| `{testGeneratorSkill}` | `"{repoName}-test-generator"` or NOT INSTALLED message |
| `{today's date}` | ISO date at scaffold time (YYYY-MM-DD) |
| `{meta-skill version}` | `version:` field from manh-test-orchestrator frontmatter |

---

## Template

````markdown
---
name: {repoName}-test-orchestrator
version: {today's date — YYYY-MM-DD}
protocol_version: {meta-skill version}
scaffolded-from: manh-test-orchestrator@{meta-skill version}
scaffolded-date: {today's date — YYYY-MM-DD}
description: |
  Validates test coverage after implementation by reading a plan*.md artifact,
  identifying coverage gaps, delegating to peer skills for scenario identification
  and test generation, and writing a structured validation report.
  Non-interactive pipeline — no grooming, no questions, deterministic from plan input.
trigger: manual
---

# {repoName}-test-orchestrator

Post-implementation test validation for {repoName}. Reads a plan file, analyzes
coverage gaps, delegates to peer skills, and writes a validation report.

## When to Use

Invoke after implementation is complete — before creating a PR — to verify test
coverage against the plan's Definition of Done.

## Prerequisites

| Dependency | Status |
|------------|--------|
| `{testScenariosSkill}` | {INSTALLED / NOT INSTALLED — run /manh-test-scenario-identifier first} |
| `{testGeneratorSkill}` | {INSTALLED / NOT INSTALLED — run /manh-test-generator first} |

## Invocation

```
validate: plan/story/2026-Q2/AI-55100-plan.md     ← explicit path
validate: AI-55100                                  ← ticket ID — resolves via glob
validate:                                           ← no arg — picks most recent plan by mtime
```

## Repo Context

| Field | Value |
|-------|-------|
| Repo name | {repoName} |
| Test naming convention | {namingConvention} |
| Coverage command | {coverageCommand} |
| Coverage threshold | {coverageThreshold} |
| Test scenarios skill | {testScenariosSkill} |
| Test generator skill | {testGeneratorSkill} |

---

## Execution Protocol

This skill runs a **four-stage non-interactive pipeline**. No prompts. No gates.
All decisions are deterministic from the plan content.

Full stage definitions:
→ [PIPELINE-PROTOCOL.md](./references/PIPELINE-PROTOCOL.md)

```
STAGE 1 — PARSE PLAN
STAGE 2 — GAP ANALYSIS
STAGE 3 — SCENARIO DELEGATION  →  {testScenariosSkill} (diff mode)
STAGE 4 — TEST GENERATION      →  {testGeneratorSkill} (per gap)
─────────────────────────────────────────────────────────────────
OUTPUT   — Write validation-report.md + print verdict summary
```

Validation report structure:
→ [VALIDATION-REPORT-FORMAT.md](./references/VALIDATION-REPORT-FORMAT.md)

## Tips

- **Pair with change-planner**: Run `{repoName}-change-planner` first to generate the plan,
  implement the changes, then run this validator to verify coverage.
- **GREEN severity**: All planned test coverage is satisfied. Proceed to PR.
- **YELLOW severity**: Minor gaps. Review the Remaining Actions table and close gaps before PR.
- **RED severity**: Significant gaps or regression risks. Address all P1 actions before PR.
- **Re-run after fixing gaps**: After applying generated scaffolds or fixing regression tests,
  re-run the validator to confirm severity drops to GREEN.
````
