# Stage 3 output format

Write **two separate files** into `<out_dir>` (= `{workspace}/{product-repo}/products/{product}/test/<jira_id>/`):

| File | Purpose |
|---|---|
| `<jira_id>_test-scenarios.md` | Human-readable artifact — scenario table. **No JSON block.** |
| `<jira_id>_test-scenarios.json` | Machine-readable artifact — full structured data for stage 4. |

---

## MD artifact layout (`<jira_id>_test-scenarios.md`)

1. `## Test Scenarios` — table with columns: `Scenario ID | One-liner | Polarity | Risk | Covers | Source`.
2. `## Dropped` — list of dropped scenario ids with reasons, or "None."

> Do **not** include any JSON block in this file.

---

## JSON artifact schema (`<jira_id>_test-scenarios.json`)

```json
{
  "story_id": "<jira_id>",
  "scenarios": [
    {
      "id": "S001",
      "one_liner": "Verify line discount applies to a regular item paid by card in USD",
      "polarity": "Positive",
      "risk": "med",
      "covers": ["C001"],
      "source": "combination"
    },
    {
      "id": "S014",
      "one_liner": "Verify a clear error is shown when paying a gift-card item with a gift card",
      "polarity": "Negative",
      "risk": "high",
      "covers": ["C002"],
      "source": "combination"
    },
    {
      "id": "S021",
      "one_liner": "Verify the discount change is written to the order change log",
      "polarity": "Positive",
      "risk": "med",
      "covers": [],
      "source": "plan-derived"
    }
  ],
  "dropped": []
}
```

### Field rules
- `story_id` — the `<jira_id>` derived in Phase 0.
- `scenarios[].source` — `"combination"` (derived from a combination row) or `"plan-derived"` (added from AC/plan intent not expressible by combinatorics).
- `scenarios[].covers` — array of combination ids from stage 2 this scenario exercises; empty array for `plan-derived` scenarios.
- `scenarios[].polarity` — `"Positive"`, `"Negative"`, or `"Boundary"`.
- `dropped` — array of `{"id": "C00X", "reason": "..."}` for any combination id not covered by any scenario.

---

## Final Gate (before stage 4)
- [ ] Phase 1b integrity audit completed and reported before any one-liner was written.
- [ ] No surviving combination row contains a contradictory parameter set (P1–P5).
- [ ] Every combination id appears in at least one scenario's `covers[]` (or is in `dropped[]` with a reason).
- [ ] All behavioral scenarios (`BS*`) are retained and their one-liners are derived from their labels, not their parameter vectors.
- [ ] Every plan acceptance criterion maps to ≥1 scenario.
- [ ] One-liners are single sentences, behavior-first, with polarity tags.
- [ ] JSON file is valid JSON and `scenarios[]` is non-empty.
- [ ] MD file contains no JSON blocks.
