# Phase 0 — Resolve inputs (mandatory, blocking)

Run in order before anything else. Wait for each answer before proceeding to the next step.

> **`askUser()` is mandatory for every user-facing question in this phase.**
> Always provide labelled options with one option clearly marked as the recommendation.
> Never ask open-ended free-text questions without options.
> Never infer silently from context when user input is required.
> If a question receives an unreadable or invalid answer, re-ask using `askUser()` — do not assume or search.

---

## Step 1 — Ask for the plan path

Use `askUser()`:
```
Question : "What is the full path to your plan file?"
Options  :
  • {workspace}/{product-repo}/products/{product}/test/<jira_id>/<jira_id>-plan.md
    (Recommended — standard plan location)
  • I'll type a custom path
```

Read that exact file. If wrong or unreadable, re-ask using `askUser()` — do not search or assume.

---

## Step 2 — Derive `<jira_id>` and `<product>`, resolve `<out_dir>`

From the confirmed plan path, extract silently without asking the user:

- **Extract `<jira_id>`** — match the JIRA-style ticket id in the filename or path segments (e.g. `POS-181833`).
- **Extract `<product>`** — infer from the JIRA key prefix (`POS-`/`SIF-` → `pos`/`sif`, `OM-` → `oms`, `AI-` → `mascp`, `MATM-` → `matm`, `SUP-` → `wms`), or from the folder name immediately after `products/` in the path.

Resolve `{product-repo}` for `{product}` from the static table below:

| Product slug | Jira prefix | Repo | `<out_dir>` |
|---|---|---|---|
| `pos` | `POS` | `active-store` | `{workspace}/active-store/products/pos/test/<jira_id>/` |
| `sif` | `SIF` | `active-store` | `{workspace}/active-store/products/sif/test/<jira_id>/` |
| `oms` | `OM` | `active-order` | `{workspace}/active-order/products/oms/test/<jira_id>/` |
| `mascp` | `AI` | `active-planning` | `{workspace}/active-planning/products/mascp/test/<jira_id>/` |
| `matm` | `MATM` | `active-transportation` | `{workspace}/active-transportation/products/matm/test/<jira_id>/` |
| `wms` | `SUP` | `active-warehouse` | `{workspace}/active-warehouse/products/wms/test/<jira_id>/` |

- **Set `<out_dir>`** = `{workspace}/{product-repo}/products/{product}/test/<jira_id>/` (MANDATORY — never `.manh-ai-harness/` or any other location).

Announce what was derived:
> "Derived JIRA id `<jira_id>`, product `<product>`, output dir `<out_dir>`."

**Only if either value cannot be extracted** (path does not match expected conventions), use `askUser()`:
```
Question : "I couldn't extract <jira_id> / <product> from the plan path <plan_path>. Please clarify:"
Options  :
  • The JIRA id is <best_guess_jira_id> and product is <best_guess_product>
    (Recommended — my best guess)
  • I'll provide the correct JIRA id and product name
```

Update `<jira_id>`, `<product>`, and `<out_dir>` with the user-supplied values before continuing.

---

## Step 3 — Auto-probe for stage-3 scenarios files

**Never ask the user for the scenarios file path upfront.** Probe in priority order:

1. Standard canonical location (preferred):
   ```
   <out_dir>/<jira_id>_test-scenarios.md
   <out_dir>/<jira_id>_test-scenarios.json
   ```
2. Same directory as the plan file.
3. Glob search: `**/<jira_id>*test-scenarios*` under the workspace root.

**If both files are found at any location:** load them silently — do NOT ask the user. Announce:
> "Found scenarios files at `<resolved_path>`. Proceeding."

If a `.json` companion exists alongside the `.md`, prefer reading it for the MACHINE_BLOCK
(it is the authoritative structured artifact).

**Only if no file is found after exhausting all three locations:** inform the user and use `askUser()`:
```
Question : "I couldn't find <jira_id>_test-scenarios.md/json anywhere. Where is it?"
Options  :
  • Place it at the standard location first, then continue
    (Recommended)
  • I'll provide the full path now
```

Read the user-supplied path exactly as given; if wrong or unreadable, re-ask using `askUser()`.

---

## Step 4 — Ask scope

Use `askUser()`:
```
Question : "Which scenarios should I expand into test cases?"
Options  :
  • All scenarios
    (Recommended — generates the complete test suite)
  • A single scenario by ID — I'll specify it
```

If the user selects "A single scenario by ID", follow up with `askUser()`:
```
Question : "Which scenario ID should I expand?"
Options  :
  • <first_scenario_id_from_file>  (Recommended — first in file)
  • I'll type the scenario ID
```

Default to **all** if the user does not specify.

---

## Step 5 — Validate scenarios files

- Parse `<jira_id>_test-scenarios.json` → `scenarios[]` must be non-empty.
- `<jira_id>_test-scenarios.md` must be readable and non-empty.

If either check fails, STOP and use `askUser()`:
```
Question : "The scenarios file for <jira_id> is missing or empty. How would you like to proceed?"
Options  :
  • Re-run stage 3 (manh-test-scenario-identifier) first, then come back
    (Recommended)
  • I'll provide an alternate scenarios file path
```

---

## ✅ Mandatory gate — cannot proceed past Phase 0 unless ALL are true

- [ ] `<plan_path>` supplied via `askUser()` and readable.
- [ ] `<jira_id>` and `<product>` derived from the plan path (auto; `askUser()` only if extraction fails).
- [ ] `{product}` slug found in the static Product/Repo table and `{product-repo}` resolved.
- [ ] `<out_dir>` set to `{workspace}/{product-repo}/products/{product}/test/<jira_id>/` (never `.manh-ai-harness/` or any other location).
- [ ] `<jira_id>_test-scenarios.md` readable and non-empty (auto-probed; `askUser()` only on failure).
- [ ] `<jira_id>_test-scenarios.json` readable and `scenarios[]` non-empty (auto-probed; `askUser()` only on failure).
- [ ] Scope (all or single id) confirmed via `askUser()`.

If any check fails, STOP and re-ask using `askUser()` with options and a recommendation.
