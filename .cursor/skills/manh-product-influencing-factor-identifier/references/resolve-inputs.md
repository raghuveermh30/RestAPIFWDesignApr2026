# Phase 0 — Product & input resolution (mandatory, blocking)

Do this first. Do not read the plan, match factors, or run any later phase until BOTH the plan and the product `influencing-factors.md` are confirmed present.

## Resolve product-dir

1. **Identify `{product}` slug** from the user's input (e.g. `pos`, `mascp`, `oms`, `sif`, `matm`, `wms`). Ask once if not stated.
2. **Resolve `{product-repo}`** from the static table below.

   | Product | `{product-repo}` |
   |---|---|
   | `pos` | `active-store` |
   | `sif` | `active-store` |
   | `oms` | `active-order` |
   | `mascp` | `active-planning` |
   | `matm` | `active-transportation` |
   | `wms` | `active-warehouse` |
3. **Ask for the workspace path** — the root directory containing local repo clones (e.g. `/Users/dev/repos`). Validate it exists; if not, STOP and re-ask.
4. **Construct `product-dir`** = `{workspace}/{product-repo}/products/{product}/`. Validate the path exists; if not, STOP and re-ask.

1. **Locate the master index** by probing `{product-dir}/influencing-factors.md`.
   - Found → this is the master index. Proceed.
   - Not found → ask the user for the full local path to the `influencing-factors.md` file. Do not fall back to another location and do not invent factors.
2. **Ask for the plan path.** Full local-system path to the plan file (do NOT ask for a jira id, do NOT assume a filename/location). Read it; if missing/unreadable, STOP and re-ask.
   - **Derive `<jira_id>`** for output naming: JIRA id in the plan filename (e.g. `POS-119192-plan.md` → `POS-119192`), else a ticket id in the plan title/header, else a slug of the title. Confirm with the user before writing.
3. **Resolve `<out_dir>`** = `{product-dir}/test/<jira_id>`.

   **Hard rules — enforce at write time, not just at resolution time:**
   - MUST write to `{workspace}/{product-repo}/products/{product}/test/<jira_id>/` (derived from the table above).
   - MUST NOT write to `.manh-ai-harness/` or any other location.
   - If `{workspace}/{product-repo}/` does not exist on disk, STOP and tell the user.
   - Create the `test/<jira_id>/` leaf directory if absent, but only after the active-repo root is confirmed present.

## Q&A protocol — one question at a time
All open questions (missing inputs, confirmations, split-vs-merge decisions) MUST be asked **one at a time**. For each question:
- Present 2–4 numbered options.
- Label the recommended choice with **"Recommendation:"** and a brief rationale.
- Wait for the user's answer before proceeding to the next question.

## ✅ Mandatory gate — cannot proceed without both
- [ ] `influencing-factors.md` exists and is readable (found at `<repo_path>/products/<product>/influencing-factors.md` or at user-supplied path).
- [ ] the plan file at the user-supplied path exists and is readable.

If either is missing, STOP and ask the user; do not continue to Phase 1.
