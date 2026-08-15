# Completion Report Format

Print one per-skill block for every skill processed, then a batch summary at the end.

---

## Per-skill block — Meta-skill (successful)

```
[REGEN] ============================================================
[REGEN] ✓ {skill-name} → {target-repo-name}
[REGEN]
[REGEN] Type:       Meta-skill
[REGEN] Generated:  {generated-skill-name}
[REGEN] Source:     {skill-name}@{meta-skill-version}
[REGEN] Lines:      {line-count}
[REGEN]
[REGEN] Checks:     frontmatter ✓   scaffolded-from ✓   no placeholders ✓   length ✓
[REGEN]
[REGEN] Distribution (pass 1 — meta-skill):
[REGEN]   ✓ .claude/skills/   ✓ .windsurf/skills/   ✓ .cursor/skills/
[REGEN]   ✓ .opencode/skills/ ✓ .devin/skills/
[REGEN]
[REGEN] Distribution (pass 2 — generated skill):
[REGEN]   ✓ .claude/skills/   ✓ .windsurf/skills/   ✓ .cursor/skills/
[REGEN]   ✓ .opencode/skills/ ✓ .devin/skills/
[REGEN]
[REGEN] skill-versions.md: updated {generated-skill-name} → {today}
[REGEN] ============================================================
```

---

## Per-skill block — Reference/Utility skill (successful)

```
[REGEN] ============================================================
[REGEN] ✓ {skill-name} → {target-repo-name}
[REGEN]
[REGEN] Type:       Reference skill
[REGEN] Version:    {skill-version}
[REGEN]
[REGEN] Distribution:
[REGEN]   ✓ .claude/skills/   ✓ .windsurf/skills/   ✓ .cursor/skills/
[REGEN]   ✓ .opencode/skills/ ✓ .devin/skills/
[REGEN]
[REGEN] skill-versions.md: updated {skill-name} → {today}
[REGEN] ============================================================
```

---

## Per-skill block — Failed

```
[REGEN] ============================================================
[REGEN] ✗ {skill-name} → {target-repo-name}   FAILED
[REGEN]
[REGEN] Reason: {exact error message from EXECUTION-PROTOCOL.md}
[REGEN] ============================================================
```

---

## Batch summary (always printed last)

```
[REGEN] ════════════════════════════════════════════════════════════
[REGEN] Propagation complete — {target-repo-name}
[REGEN]
[REGEN]   Processed : {total} skill(s)
[REGEN]   Succeeded : {n} ({list of skill names})
[REGEN]   Failed    : {n} ({list of skill names, or "none"})
[REGEN]
[REGEN] Target repo : {target-repo-path}
[REGEN] ════════════════════════════════════════════════════════════
```
