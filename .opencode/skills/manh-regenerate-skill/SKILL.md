---
name: manh-regenerate-skill
version: 2026-06-11
description: Propagates harness-builder skill updates into a target repo. Scans harness-builder/skills/ for available skills, asks which to copy and which repo to copy them into, then copies reference skills as-is and re-executes meta-skills to regenerate the repo-specific generated skill. Runs setup.sh twice per meta-skill to ensure both the meta-skill and the generated skill are distributed to all IDE directories.
trigger: manual
---

<what-to-do>

You are running inside `genai-productivity-lib`. Ask the engineer two questions in sequence before doing anything else:

**Question 1 — Which skills to copy:**
Scan `harness-builder/skills/` and list every skill directory found. Present the full list and ask the engineer to pick one or more (they can say "all" or name specific ones).

**Question 2 — Which target repo:**
Ask for the full path to the target repo (e.g. `/Users/name/repos/component-ai-forecast`). Confirm the path exists before proceeding.

Once you have both answers, execute the full copy-and-propagate workflow defined in [EXECUTION-PROTOCOL.md](./references/EXECUTION-PROTOCOL.md) for each chosen skill.

Use the skill classification table in [SKILL-LOOKUP-TABLE.md](./references/SKILL-LOOKUP-TABLE.md) to determine whether each chosen skill is a **meta-skill** (copy + regenerate) or a **reference/utility skill** (copy only). The execution path differs between the two types.

Print the completion report using the format in [COMPLETION-REPORT.md](./references/COMPLETION-REPORT.md) after all skills are processed.

</what-to-do>

<supporting-info>

## Where this skill runs

This skill is invoked from **`genai-productivity-lib`** — the harness library repo. It reads skills from `harness-builder/skills/` (source) and copies them into a separate target repo supplied by the engineer.

## Why setup.sh runs twice for meta-skills

When propagating a meta-skill (e.g. `manh-change-planner`):

1. **First `setup.sh` run** — after copying the meta-skill into `.manh-ai-harness/skills/`, run `setup.sh --force` so the meta-skill itself is linked into all IDE directories (`.claude/skills/`, `.windsurf/skills/`, etc.). This makes the meta-skill available before it is executed.

2. **Meta-skill execution** — invoke the meta-skill using the harness artifacts in the target repo to generate the repo-specific skill (e.g. `component-ai-forecast-change-planner`).

3. **Second `setup.sh` run** — after the generated skill is written to `.manh-ai-harness/skills/`, run `setup.sh --force` again so the newly generated skill is also linked into all IDE directories.

Skipping the first run means the meta-skill may not be available to the AI at execution time. Skipping the second run means the generated skill never reaches the IDE directories and engineers cannot invoke it.

## Canonical write target

All skills (meta-skills and generated skills alike) must be written to:
```
{target-repo}/.manh-ai-harness/skills/{skill-name}/
```

Never write directly to `.claude/skills/`, `.windsurf/skills/`, `.cursor/skills/`, `.opencode/skills/`, or `.devin/skills/`. Those are distribution targets populated exclusively by `setup.sh`.

</supporting-info>
