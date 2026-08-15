# Execution Protocol

Detailed step-by-step instructions for `manh-regenerate-skill`. Executed after the engineer
has answered both questions (which skills, which target repo path).

---

## Step 0 — Validate inputs

1. Confirm `{target-repo}/.manh-ai-harness/` exists:
   ```bash
   ls {target-repo}/.manh-ai-harness/
   ```
   If absent → abort:
   ```
   [REGEN] ✗ {target-repo}/.manh-ai-harness/ not found.
             This does not look like a harness-enabled repo. Run manh-build-harness there first.
   ```

2. Confirm `{target-repo}/.manh-ai-harness/setup.sh` exists:
   ```bash
   ls {target-repo}/.manh-ai-harness/setup.sh
   ```
   If absent → abort:
   ```
   [REGEN] ✗ setup.sh not found at {target-repo}/.manh-ai-harness/setup.sh.
             Copy it first: cp harness-builder/cli/setup.sh {target-repo}/.manh-ai-harness/setup.sh
   ```

3. For each chosen skill, confirm the source directory exists:
   ```bash
   ls harness-builder/skills/{skill-name}/
   ```
   If absent → skip that skill with a warning and continue with the rest.

---

## Step 1 — Classify each chosen skill

Look up each skill name in [SKILL-LOOKUP-TABLE.md](./SKILL-LOOKUP-TABLE.md):
- **Meta-skill** → follow Path A below
- **Reference / Utility skill** → follow Path B below

Process skills one at a time. Complete all steps for one skill before starting the next.

---

## Path A — Meta-skill (copy + regenerate)

### A1 — Copy the meta-skill to the canonical source location

```bash
cp -r harness-builder/skills/{skill-name} {target-repo}/.manh-ai-harness/skills/
```

If the directory already exists in the target repo, overwrite it (this is an update run).

### A2 — First setup.sh run (distribute the meta-skill to IDE directories)

```bash
bash {target-repo}/.manh-ai-harness/setup.sh --force
```

This ensures the meta-skill is available in all IDE tool directories before it is executed.
Capture the output and confirm the meta-skill appears in the copy list.

### A3 — Pre-flight artifact check

Look up the meta-skill in the **Required Artifacts** table in [SKILL-LOOKUP-TABLE.md](./SKILL-LOOKUP-TABLE.md).

For each required artifact, check:
```bash
ls {target-repo}/.manh-ai-harness/{artifact-file}
```

- Missing **required** artifact → abort this meta-skill:
  ```
  [REGEN] ✗ Cannot regenerate {skill-name}: required artifact '{file}' not found
            in {target-repo}/.manh-ai-harness/. Run the appropriate analyzer first.
  ```
- Missing **optional** artifact → warn and continue:
  ```
  [REGEN] ⚠ Optional artifact '{file}' absent — generated skill will have reduced context.
  ```

### A4 — Read the meta-skill's generation protocol

Read the full content of:
```
{target-repo}/.manh-ai-harness/skills/{skill-name}/SKILL.md
```

Read all required (and available optional) harness artifacts from `{target-repo}/.manh-ai-harness/`
completely — no summarization, no truncation. Identify the repo name from `repo-analysis.md`
(look for `repo-name:` or `name:` field; fall back to the basename of the target repo path).

### A5 — Execute the meta-skill's generation protocol

Follow the generation instructions in the meta-skill's SKILL.md exactly. Substitute all
placeholder tokens:

| Token | Replacement |
|---|---|
| `[repo-name]` | Actual repo name from A4 |
| `{repo-name}` | Same |
| Entity names | From `entity-analysis-report.md` |
| Package paths | From `repo-analysis.md` |
| Test conventions | From `test-analysis.md` |
| Key-learnings guardrails | From `key-learnings.md` |

Prepend these fields to the generated skill's YAML frontmatter:
```yaml
scaffolded-from: {skill-name}@{meta-skill-version}
protocol_version: {meta-skill-version}
scaffolded-date: {today}
version: {today}
```

Where `{meta-skill-version}` = the `version:` value from the meta-skill's SKILL.md frontmatter
and `{today}` = today's date in `YYYY-MM-DD` format.

### A6 — Write the generated skill to the canonical source location

```bash
mkdir -p {target-repo}/.manh-ai-harness/skills/{generated-skill-name}
# Then write the generated SKILL.md content to:
# {target-repo}/.manh-ai-harness/skills/{generated-skill-name}/SKILL.md
```

**Never write to `.claude/skills/`, `.windsurf/skills/`, or any other IDE directory.**

### A7 — Post-write verification

Run these checks on the generated file:

| Check | Pass condition |
|---|---|
| Frontmatter present | Line 1 is `---` |
| `scaffolded-from:` present | Field exists with correct value |
| No unreplaced placeholders | No literal `[repo-name]` or `{repo-name}` text in body |
| Actual repo name in body | Repo name string appears at least once |
| File length sufficient | > 200 lines |

If any check fails:
```
[REGEN] ✗ Verification failed for {generated-skill-name}: {which check failed}.
          The file has been written. Do NOT run setup.sh until this is resolved.
```
Do not proceed to A8 for this skill.

### A8 — Second setup.sh run (distribute the generated skill to IDE directories)

```bash
bash {target-repo}/.manh-ai-harness/setup.sh --force
```

This distributes the newly generated skill into all IDE tool directories. Confirm
the generated skill name appears in the copy output.

### A9 — Update skill-versions.md

Read `{target-repo}/.manh-ai-harness/skill-versions.md`.

Find the row for `{generated-skill-name}`:
- If row **exists**: update the `Version` and `Last Updated` columns; preserve `Installed` date.
- If row **does not exist**: append a new row.

Format:
```
| {generated-skill-name} | {meta-skill-version} | {original-install-date} | {today} |
```

Also update (or add) the row for the meta-skill itself:
```
| {skill-name} | {meta-skill-version} | {original-install-date} | {today} |
```

---

## Path B — Reference / Utility skill (copy only)

### B1 — Copy the skill to the canonical source location

```bash
cp -r harness-builder/skills/{skill-name} {target-repo}/.manh-ai-harness/skills/
```

If the directory already exists, overwrite it.

For skills that include executable scripts (e.g. `manh-ff-cleanup`), ensure scripts are executable:
```bash
chmod +x {target-repo}/.manh-ai-harness/skills/{skill-name}/scripts/*.sh 2>/dev/null || true
```

### B2 — setup.sh run (distribute to IDE directories)

```bash
bash {target-repo}/.manh-ai-harness/setup.sh --force
```

### B3 — Update skill-versions.md

Update or add the row for this skill in `{target-repo}/.manh-ai-harness/skill-versions.md`:
```
| {skill-name} | {skill-version} | {original-install-date} | {today} |
```

Where `{skill-version}` = the `version:` field from `harness-builder/skills/{skill-name}/SKILL.md`.

---

## Error messages

| Situation | Message |
|---|---|
| `.manh-ai-harness/` missing | `[REGEN] ✗ {target-repo}/.manh-ai-harness/ not found. Run manh-build-harness there first.` |
| `setup.sh` missing | `[REGEN] ✗ setup.sh not found. Copy: cp harness-builder/cli/setup.sh {target-repo}/.manh-ai-harness/setup.sh` |
| Source skill not in harness-builder | `[REGEN] ✗ harness-builder/skills/{skill-name}/ not found. Skipping.` |
| Required artifact missing | `[REGEN] ✗ Cannot regenerate {skill}: required artifact '{file}' not found in .manh-ai-harness/. Run the appropriate analyzer first.` |
| Placeholder found in generated file | `[REGEN] ✗ Generated file contains unreplaced placeholder text. Do NOT run setup.sh until resolved.` |
| Generated file too short | `[REGEN] ✗ Generated file is only {N} lines — expected >200. Re-run with full artifact context.` |
