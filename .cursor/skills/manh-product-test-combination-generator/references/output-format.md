# Stage 2 output format

This stage writes **two files** into `<out_dir>` — the same directory as the stage-1 output, supplied by the user in Phase 0:

| File | Purpose |
|---|---|
| `<jira_id>_test-combinations.md` | Human-readable artifact — optimization summary and combinations table. **No JSON block.** |
| `<jira_id>_test-combinations.json` | Machine-readable artifact — full structured data for downstream stages. |

No `.pict` or `.pict.out` files are written to disk; the PICT model and raw output are kept in memory only.

---

## MD artifact layout (`<jira_id>_test-combinations.md`)

1. `## Optimization Summary` — before/after row counts, seeded rows, pruned rows, coverage notes.
2. `## Combinations` — human table with one column per factor + `Risk` + `Source`.

> Do **not** include any JSON block in this file.

---

## JSON artifact schema (`<jira_id>_test-combinations.json`)

```json
{
  "story_id": "<jira_id>",
  "strength": 2,
  "parameters": ["Feature Flag","Platform","Item Type","Discount","Tender","Currency Format"],
  "config_parameters": ["Feature Flag","Platform"],
  "guard_invariant": [
    {"factor":"Order Type","collapsed_to":"Return Order",
     "reason":"plan: fix keyed on scanInQueue, order-type independent"}
  ],
  "pict_rows": 16,
  "final_rows": 14,
  "combinations": [
    {"id":"C001","values":{"Feature Flag":"ON","Platform":"iOS","Item Type":"Regular",
      "Discount":"LineDiscount","Tender":"Card","Currency Format":"USD"},
      "risk":"med","source":"pict"},
    {"id":"C002","values":{"Feature Flag":"OFF","Platform":"Windows","Item Type":"GiftCard",
      "Discount":"None","Tender":"Cash","Currency Format":"JPY"},
      "risk":"high","source":"plan-seed"}
  ]
}
```

### Field rules
- `story_id` — the `<jira_id>` derived in Phase 0.
- `strength` — PICT strength used (2 default, 3 for high-risk/all-P0 clusters).
- `config_parameters` — subset of `parameters` flagged `is_config: true`.
- `guard_invariant` — factors collapsed to one value with justification; empty array if none.
- `combinations[].source` — `"pict"` (generated) or `"plan-seed"` (manually seeded from AC/Test Plan).
- `combinations[].risk` — `"high"` / `"med"` / `"low"` per priority weighting.

---

## Final Gate (before stage 3)
- [ ] PICT model built and parsed in memory (no `.pict` or `.pict.out` files on disk).
- [ ] Every applying factor appears as a parameter.
- [ ] Every config factor (`is_config: true`) is a parameter and each value appears in ≥1 combination (or `excluded_by_constraint`).
- [ ] Priority coverage holds: every P0 value in ≥1 row; every P1 key value (Positive + Negative/Boundary); every P2 factor ≥1 representative (or `excluded_by_constraint`).
- [ ] Each high-risk pair and each Negative/Boundary value is covered by ≥1 row.
- [ ] Any factor the plan proves does not affect the outcome is collapsed to one value and listed in `guard_invariant[]` with justification.
- [ ] JSON file is valid JSON and `combinations[]` is non-empty; each combination has a stable `id`.
- [ ] MD file contains no JSON blocks.
