# Phase 0 — Input resolution (mandatory, blocking)

Do this first. Do not load factors, build the PICT model, or run any later phase until all inputs are confirmed.

## Resolution steps

1. **Identify `{product}` slug** — derive from the JIRA key prefix without asking:

   | Jira key prefix | Product slug | Git repo                | Full `<out_dir>` (absolute)                                                        |
   |---|---|---|---|
   | `POS`           | `pos`        | `active-store`          | `{workspace}/active-store/products/pos/test/<jira_id>/`           |
   | `SIF`           | `sif`        | `active-store`          | `{workspace}/active-store/products/sif/test/<jira_id>/`           |
   | `OM`            | `oms`        | `active-order`          | `{workspace}/active-order/products/oms/test/<jira_id>/`           |
   | `AI`            | `mascp`      | `active-planning`       | `{workspace}/active-planning/products/mascp/test/<jira_id>/`      |
   | `MATM`          | `matm`       | `active-transportation` | `{workspace}/active-transportation/products/matm/test/<jira_id>/` |
   | `SUP`           | `wms`        | `active-warehouse`      | `{workspace}/active-warehouse/products/wms/test/<jira_id>/`       |

   Only ask the user if the JIRA prefix is not in this table.

2. **Resolve `{workspace}`** — ask the user for the root directory containing local repo clones (the directory that contains `active-store/`, `active-order/`, etc. as siblings). Validate it exists; if not, STOP and re-ask.

3. **Derive `<jira_id>`** — from the plan file path or user input. No need to ask if it can be parsed from the plan filename.

4. **Construct `<out_dir>`** (absolute) = `{workspace}/{git-repo}/products/{product}/test/<jira_id>/` using the table in step 1.

5. **Locate `<jira_id>_influencing-factors.json`** inside `<out_dir>`. Search `<out_dir>` for any file matching `*influencing-factors.json` if the exact name is uncertain. If not found, STOP — tell the user to complete stage 1 (`manh-product-influencing-factor-identifier`) first.

6. **Validate** — JSON parses and `user_finalized` is `true`. If false or missing, STOP and tell the user to complete/re-run stage 1.

## ✅ Mandatory gate — cannot proceed past Phase 0 unless ALL are true

- [ ] `{product}` slug resolved from JIRA key prefix (table above).
- [ ] `{workspace}` confirmed (root directory containing local repo clones).
- [ ] `<out_dir>` constructed and exists on disk.
- [ ] `<jira_id>` confirmed (from plan path or user input).
- [ ] `<out_dir>/<jira_id>_influencing-factors.json` exists and is readable.
- [ ] Factors JSON parses and `user_finalized` is `true`.

If any check fails, STOP and re-ask only for the specific missing piece. Do not ask for information that can be derived.
