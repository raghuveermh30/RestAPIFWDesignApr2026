---
name: manh-test-orchestrator
version: 2026-06-11
description: |
  Meta-skill that generates a repo-specific [repo]-test-orchestrator skill.
  The generated skill validates test coverage after implementation by reading a plan*.md
  artifact and delegating to [repo]-test-scenarios and [repo]-test-generator to close gaps.
  Non-interactive pipeline pattern (4 deterministic stages, no grooming phase).
  Plan file is the single required input (YAML header + Sections 2, 3, 5).
  Output: {ticketId}-validation.md co-located with the plan it validates.
trigger: manual
---

<what-to-do>

Generate a repo-specific `[repoName]-test-orchestrator` skill by reading the repo's
harness artifacts, extracting embedding values, scaffolding the generated skill from the
template, and running the linker.

**Step 0 — Read artifacts** from `.manh-ai-harness/`:

1. `test-analysis.md` — extract F7 (naming convention) and F2 (coverage command + threshold)
2. `context-profile.md` — extract `repoName` and `componentName`
3. `repo-analysis.md` — fallback for `repoName` if context-profile is absent; use directory name as final fallback

**Step 1 — Determine installed peer skills** by checking for:
```
.manh-ai-harness/skills/{repoName}-test-scenarios/SKILL.md
.manh-ai-harness/skills/{repoName}-test-generator/SKILL.md
```
Record `testScenariosInstalled` and `testGeneratorInstalled` (true/false). If either is absent,
continue — embed a warning in the generated skill's Prerequisites section.

**Step 2 — Extract embedding values**:
```
repoName            ← context-profile.md or directory name
namingConvention    ← test-analysis.md F7 (e.g., {ClassName}Test.java)
coverageCommand     ← test-analysis.md F2 (e.g., ./gradlew jacocoTestReport)
coverageThreshold   ← test-analysis.md F2 coverage gate (e.g., 80%)
testScenariosSkill  ← "{repoName}-test-scenarios" (or "NOT INSTALLED — run /manh-test-scenario-identifier first")
testGeneratorSkill  ← "{repoName}-test-generator" (or "NOT INSTALLED — run /manh-test-generator first")
```

**Step 3 — Scaffold generated skill** by writing
`.manh-ai-harness/skills/{repoName}-test-orchestrator/SKILL.md` using the template in
[GENERATED-SKILL-TEMPLATE.md](./references/GENERATED-SKILL-TEMPLATE.md). Substitute all
six embedding values. Set `version:` to today's date, `protocol_version:` to this
meta-skill's `version:` field, and `scaffolded-from: manh-test-orchestrator@{protocol_version}`.

**Step 4 — Run the linker**:
```bash
bash .manh-ai-harness/setup.sh [--force]
```

**Step 5 — Notify the user**:
```
Generated: {repoName}-test-orchestrator

Location : .manh-ai-harness/skills/{repoName}-test-orchestrator/SKILL.md
Symlinked: .claude/skills/{repoName}-test-orchestrator/

Peer skills wired:
  Scenarios : {testScenariosSkill}
  Generator : {testGeneratorSkill}
  Naming    : {namingConvention}
  Coverage  : {coverageCommand}
  Threshold : {coverageThreshold}

Prerequisites satisfied: {YES | NO — list missing}

Invoke with:
  validate: plan/story/2026-Q2/AI-55100-plan.md
  validate: AI-55100                              ← resolves path automatically
  validate:                                       ← picks most recent plan
```

</what-to-do>

<supporting-info>

## When to Use

Invoke this skill after `manh-test-analyzer` (or `manh-build-harness` Phase 2c) has produced
`test-analysis.md`. The generated skill is a post-implementation tool — engineers invoke it
after code changes are done, before creating a PR.

Do **not** invoke this skill:
- Before `manh-repo-analyzer` has produced `repo-analysis.md`
- Before any plan files exist in `plan/` — the validator requires a plan to validate against

## Prerequisites

| Artifact | Path | Required |
|----------|------|----------|
| `test-analysis.md` | `.manh-ai-harness/test-analysis.md` | **Required** (F7 naming convention, F2 coverage command) |
| `context-profile.md` | `.manh-ai-harness/context-profile.md` | Strongly recommended (repo name, component name) |
| `repo-analysis.md` | `.manh-ai-harness/repo-analysis.md` | Fallback for repo name if context-profile absent |

## Peer Skill Dependencies

The generated skill delegates to these peer skills when available:

| Peer Skill | Used In | Purpose |
|------------|---------|---------|
| `[repo]-test-scenarios` | Stage 3 | Identifies concrete test scenarios for gap files (diff mode) |
| `[repo]-test-generator` | Stage 4 | Generates compilable test scaffolds for each gap |

If either peer skill is not installed, the generated skill emits a delegation panel
instructing the engineer to run the peer skill manually. No silent degradation.

## Reference Documents

| Document | Contents |
|---|---|
| [PIPELINE-PROTOCOL.md](./references/PIPELINE-PROTOCOL.md) | Full 4-stage pipeline: Parse Plan → Gap Analysis → Scenario Delegation → Test Generation → Output |
| [GENERATED-SKILL-TEMPLATE.md](./references/GENERATED-SKILL-TEMPLATE.md) | Complete template for the scaffolded `[repo]-test-orchestrator` skill |
| [VALIDATION-REPORT-FORMAT.md](./references/VALIDATION-REPORT-FORMAT.md) | Structure and severity legend for the `{ticketId}-validation.md` output |
| [TOOL-COMPATIBILITY.md](./references/TOOL-COMPATIBILITY.md) | Per-tool delegation support, tips, and backward compatibility notes |

</supporting-info>
