# Phase 0 — Resolve inputs (mandatory, blocking)

Run steps in order before anything else. Follow the Q&A protocol below — one question at a time, never batch.

## Q&A protocol — one question at a time
All open questions (missing inputs, confirmations, clarifications) MUST be asked **one at a time** using the `ask_user_question` tool. Each call must include:
- A short `header` (≤ 30 chars) naming the decision point.
- A clear `question` string.
- 2–4 `options`, each with a concise `label` and a descriptive `description`.
- The recommended option's `label` must end with `"(Recommended)"`.

Never batch multiple questions in a single `ask_user_question` call. Wait for the answer before proceeding.

---

### Step 1 — Ask for the change planner path (always first)
Use `ask_user_question` with:
- `header`: `"Change planner path"`
- `question`: `"What is the full local path to your change planner file (e.g. POS-123456-plan.md)?"`
- options:
  - `{ label: "Type the full path (Recommended)", description: "Provide the exact filesystem path to your plan file, e.g. /path/to/POS-123456-plan.md" }`

Read that exact file; if wrong or unreadable, re-ask using the same pattern — do not search or assume.

### Step 2 — Derive `<jira_id>` from the change planner
Attempt to extract the JIRA id in this order:
1. JIRA-style pattern in the **filename** (e.g. `POS-123456-plan.md` → `POS-123456`).
2. JIRA-style ticket reference in the plan **title or header line**.
3. Any ticket id referenced in the plan **body** (first match).

If a `<jira_id>` is found, confirm it with the user using `ask_user_question`:
- `header`: `"Confirm JIRA id"`
- `question`: `"I detected '<jira_id>' as the JIRA ticket id. Is this correct?"`
- options:
  - `{ label: "Yes, use <jira_id> (Recommended)", description: "Proceed with the detected JIRA id" }`
  - `{ label: "No, let me correct it", description: "Type the correct JIRA ticket id, e.g. POS-123456" }`

If no `<jira_id>` can be found, ask the user using `ask_user_question`:
- `header`: `"JIRA ticket id"`
- `question`: `"I could not detect a JIRA id from the plan file. Please provide the JIRA ticket id."`
- options:
  - `{ label: "Type the JIRA id (Recommended)", description: "Enter the exact JIRA ticket id, e.g. POS-123456" }`

### Step 3 — Resolve product and `<out_dir>`
Identify the `{product}` slug from the JIRA key prefix (e.g. `POS-` → `pos`, `OM-` → `oms`, `AI-` → `mascp`, `SIF-` → `sif`, `MATM-` → `matm`, `SUP-` → `wms`). If not determinable, ask once.

Resolve `{product-repo}` for `{product}` from the static table below:

| Product slug | Jira prefix | Repo | `<out_dir>` |
|---|---|---|---|
| `pos` | `POS` | `active-store` | `{workspace}/active-store/products/pos/test/<jira_id>/` |
| `sif` | `SIF` | `active-store` | `{workspace}/active-store/products/sif/test/<jira_id>/` |
| `oms` | `OM` | `active-order` | `{workspace}/active-order/products/oms/test/<jira_id>/` |
| `mascp` | `AI` | `active-planning` | `{workspace}/active-planning/products/mascp/test/<jira_id>/` |
| `matm` | `MATM` | `active-transportation` | `{workspace}/active-transportation/products/matm/test/<jira_id>/` |
| `wms` | `SUP` | `active-warehouse` | `{workspace}/active-warehouse/products/wms/test/<jira_id>/` |

`<out_dir>` = `{workspace}/{product-repo}/products/{product}/test/<jira_id>/`. Outputs MUST go here — never `.manh-ai-harness/` or any other location. Create `<out_dir>` if absent.

### Step 4 — Auto-locate the stage-2 combinations file
With `<out_dir>` resolved, check for the stage-2 artifact at:

```
<out_dir>/<jira_id>-test-combinations.json
```

- **Found** → use it silently; inform the user of the resolved path.
- **Not found** → ask the user using `ask_user_question`:
  - `header`: `"Combinations file path"`
  - `question`: `"I could not find the combinations file at the expected path. Please provide the full path to your stage-2 *-test-combinations.json file."`
  - options:
    - `{ label: "Type the full path (Recommended)", description: "Supply the exact path output by stage 2, e.g. /path/to/<jira_id>-test-combinations.json" }`

### Step 5 — Validate the combinations file
Confirm the file is a real stage-2 artifact: JSON parses and `combinations[]` is non-empty. If not, STOP and tell the user to complete/re-run stage 2 (`manh-product-test-combination-generator`) before continuing.

---

## ✅ Mandatory gate — cannot proceed past Phase 0 unless ALL are true
- [ ] `<plan_path>` supplied and readable.
- [ ] `<jira_id>` derived or user-supplied and confirmed.
- [ ] `{product}` slug resolved and found in the static Product/Repo table above.
- [ ] `<out_dir>` constructed and exists on disk (or created).
- [ ] `<combinations_path>` resolved (auto or user-supplied) and readable.
- [ ] Combinations JSON parses and `combinations[]` is non-empty.

If any check fails, STOP and re-ask. All inputs are mandatory before moving to Phase 1.
