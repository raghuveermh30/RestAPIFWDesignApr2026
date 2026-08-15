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

## Step 2 — Derive `<jira_id>`, `<product>`, `{workspace}`, and `<out_dir>`

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

**Resolve `{workspace}`:**
- If the plan path contains a recognisable `{product-repo}/products/` segment (e.g. `.../active-order/products/oms/...`), infer `{workspace}` as the grandparent of `{product-repo}` silently and announce it.
- Otherwise, use `askUser()` for the workspace root — the directory that contains `active-store`, `active-order`, etc.:
  ```
  Question : "What is your workspace root — the directory that contains active-store, active-order, etc.?"
  Options  :
    • /Users/<you>/Documents/Omni  (Recommended — my best guess from the plan path)
    • I'll type the workspace root path
  ```

**Set `<out_dir>`** = `{workspace}/{product-repo}/products/{product}/test/<jira_id>/`

> **HARD RULE — `<out_dir>` must resolve to `{workspace}/{product-repo}/products/{product}/test/<jira_id>/`.
> If it would resolve to anything else — including `.manh-ai-harness/`, the plan file's directory, or any path not under `{workspace}/{product-repo}/` — STOP immediately and re-derive. Never proceed with a wrong `<out_dir>`.**

Announce what was derived:
> "Derived JIRA id `<jira_id>`, product `<product>`, workspace `{workspace}`, output dir `<out_dir>`."

**Only if `<jira_id>` or `<product>` cannot be extracted** (path does not match expected conventions), use `askUser()`:
```
Question : "I couldn't extract <jira_id> / <product> from the plan path. Please clarify:"
Options  :
  • The JIRA id is <best_guess_jira_id> and product is <best_guess_product>
    (Recommended — my best guess)
  • I'll provide the correct JIRA id and product name
```

---

## Step 3 — Load stage-4 test-cases files from `<out_dir>` only

**The canonical location is the only automatic probe location:**
```
<out_dir>/<jira_id>_test-cases.md
<out_dir>/<jira_id>_test-cases.json
```

> **HARD RULE — never load test-cases files from any other location automatically.**
> Do NOT fall back to the plan file's directory, a download folder, or any path outside `<out_dir>`.
> Do NOT perform any glob or recursive search.
> If the files are not at `<out_dir>`, STOP and ask the user via `askUser()` — do not probe elsewhere.

**If both files are found at `<out_dir>`:** load them silently. Announce:
> "Found test-cases files at `<out_dir>`. Proceeding."

Prefer the `.json` companion for the MACHINE_BLOCK (it is the authoritative structured artifact). Parse `test_cases[]` — it must be non-empty.

**If the files are NOT found at `<out_dir>`:** STOP and use `askUser()`:
```
Question : "I could not find <jira_id>_test-cases.md / .json at <out_dir>. How would you like to proceed?"
Options  :
  • Place the files at <out_dir> first, then continue
    (Recommended — keeps all artifacts co-located)
  • I'll provide the full path to the test-cases files now
```

If the user provides a custom path, read it exactly as given. If it is readable and valid, proceed — but announce the non-standard location clearly:
> "WARNING: loading test-cases from non-standard path `<custom_path>`. Output will still be written to `<out_dir>`."
> Do NOT change `<out_dir>` to match the custom input path.

---

## Step 4 — Auto-probe for test-analysis.md (optional, silent)

**Never ask the user about `test-analysis.md` — not upfront and not on failure.**

Probe silently in priority order:

1. `{workspace}/{product-repo}/.manh-ai-harness/test-analysis.md`
2. `{workspace}/{product-repo}/` root directory.

**If found:** load silently. Announce:
> "Found test-analysis.md at `<resolved_path>`. Will align level names and target paths to it."

**If not found:** proceed silently with the default taxonomy (Unit / Component / Integration / E2E). Do NOT ask the user. This is the expected path for product-level work.

> **HARD RULE — never probe the plan file's directory for `test-analysis.md`.**

---

## Step 5 — Validate the test-cases file

Confirm the file is a real stage-4 artifact: MACHINE_BLOCK must parse and `test_cases[]` must be non-empty.

If the check fails, STOP and use `askUser()`:
```
Question : "The test-cases file for <jira_id> is missing or has an empty test_cases[]. How would you like to proceed?"
Options  :
  • Re-run stage 4 (manh-product-test-case-generator) first, then come back
    (Recommended)
  • I'll provide an alternate test-cases file path
```

---

## Step 6 — Ask for E2E repo(s) (only when E2E cases exist)

Run this step **after** Phase 3 categorization is complete and **only if** one or more cases were assigned `category: E2E`.

For each E2E case (or for all E2E cases together if they all belong to the same flow), use `askUser()`:
```
Question : "TC-<id> is categorized as E2E. Which repo should this test target?"
Header   : "E2E repo for TC-<id>"
Options  :
  • <primary product repo, e.g. active-order>
    (Recommended — primary repo for <product>)
  • <other repos known for this product>
  • I'll specify a different repo
```

Where multiple E2E cases cover the same user-visible flow, group them in a single `askUser()` call. Record the answer as `repo_name` on each affected case.

For non-E2E cases (Unit, Component, Integration), infer `repo_name` automatically — do NOT ask the user. Scan `steps[]`, `preconditions[]`, `assertions[]`, and `test_data` for file paths or service/class names and match them against the plan's codebase exploration section. Fall back to the primary product repo only when no signal is found; annotate with `repo_name_inferred: true`. Full inference rules: categorize-rules.md Rule 6.

---

## ✅ Mandatory gate — cannot proceed past Phase 0 unless ALL are true

- [ ] `<plan_path>` supplied via `askUser()` and readable.
- [ ] `{workspace}` confirmed — inferred silently from plan path when possible; `askUser()` only when not.
- [ ] `<jira_id>` and `<product>` derived from the plan path (auto; `askUser()` only if extraction fails).
- [ ] `{product}` slug found in the static Product/Repo table and `{product-repo}` resolved.
- [ ] `<out_dir>` = `{workspace}/{product-repo}/products/{product}/test/<jira_id>/` — **verified, not guessed**.
- [ ] `<jira_id>_test-cases.md` loaded from `<out_dir>` (or user-supplied path with warning); readable and non-empty.
- [ ] `<jira_id>_test-cases.json` loaded from `<out_dir>` (or user-supplied path with warning); `test_cases[]` non-empty.
- [ ] Output will be written to `<out_dir>` regardless of where input was sourced.
- [ ] Every surviving case has a `repo_name` (E2E: from `askUser()`; other levels: inferred silently).

**If any check fails, STOP. Do not proceed. Re-ask using `askUser()` with options and a recommendation.**
