# Tool Compatibility, Tips & Backward Compatibility

Reference for the `manh-test-orchestrator` meta-skill and the generated
`[repo]-test-orchestrator` skill.

---

## Tool Compatibility

Stage 3 (scenario delegation) and Stage 4 (test generation) require the current
tool to support subagent or skill delegation. Degradation behavior when delegation
is unavailable:

| Tool | Stage 3 (test-scenarios) | Stage 4 (test-generator) | Degradation |
|---|---|---|---|
| Devin CLI | Full (run_subagent) | Full (run_subagent) | None |
| Claude Code | Full (Task tool) | Full (Task tool) | None |
| OpenCode | Full | Full | None |
| Gemini CLI | Partial | Partial | Delegation panel shown |
| **Windsurf** | **Delegation panel** | **Delegation panel** | Engineer runs manually |
| **Cursor** | **Delegation panel** | **Delegation panel** | Engineer runs manually |

When a delegation panel is shown, the generated skill emits exact invocation strings
so engineers can run the peer skills manually with no ambiguity.

---

## Tips

### Workflow pairing
- **Pair with change-planner**: Run `{repoName}-change-planner` first to generate
  the plan, implement the changes, then run the validator to verify coverage.
- **Re-run after fixing gaps**: After applying generated scaffolds or fixing regression
  tests, re-run the validator to confirm severity drops to GREEN.

### Severity guidance
- **GREEN**: All planned test coverage is satisfied. Proceed to PR.
- **YELLOW**: Minor gaps. Review the Remaining Actions table and close gaps before PR.
- **RED**: Significant gaps or regression risks. Address all P1 actions before PR.

### Refreshing the generated skill
- **Invoke after `manh-test-analyzer`**: The generated skill uses naming conventions
  and coverage commands from `test-analysis.md`. Without it, gap detection is less precise.
- **Refresh after test stack changes**: If test conventions, coverage thresholds, or
  builder patterns change, re-run this meta-skill to update the embedded context.

### Peer skill availability
- **Peer skills are optional but recommended**: The validator works without
  `[repo]-test-scenarios` and `[repo]-test-generator`, but produces delegation
  panels instead of automated gap closure.

---

## Backward Compatibility

| Concern | Assessment |
|---------|-----------|
| Existing plan files | Validator handles plans without canonical frontmatter — falls back to filename; emits WARN not BLOCKED |
| Existing generated change-planner skills | Not modified. Validator is additive. |
| Existing test-scenarios and test-generator skills | Not modified. Called as-is. |
| Repos without test-scenarios or test-generator | Validator detects at Step 1 and embeds warning at scaffold time. Delegation panel shown at runtime. |
| Windsurf Ask mode | Validator emits delegation panels with exact invocation strings. No silent failure. |
