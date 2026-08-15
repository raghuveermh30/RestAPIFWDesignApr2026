# Stage 4 output format

Write **two files** to `<out_dir>` (= `{workspace}/{product-repo}/products/<product>/test/<story_id>`):

1. **`<story_id>_test-cases.md`** — human-readable: one section per test case (ID, Scenario, Polarity, Risk, Preconditions, Steps, Test Data, Assertions). No JSON block embedded in the markdown.
2. **`<story_id>_test-cases.json`** — machine-readable: the MACHINE_BLOCK only, as a standalone JSON file.

Always write both files. The `.json` is the authoritative structured artifact consumed by downstream skills (stage 5 categorizer, automation generators).

## MACHINE_BLOCK (written to `<story_id>_test-cases.json`)
```json
{
  "story_id": "<story_id>",
  "test_cases": [
    {
      "id": "TC-S014",
      "scenario_id": "S014",
      "covers": ["C002"],
      "polarity": "Negative",
      "risk": "high",
      "preconditions": [
        "Feature flag pos.giftcard.tender = ON",
        "Persona: Store Associate with Tender grant",
        "Cart seeded with 1 GiftCard item (SKU GC100, value 50.00)"
      ],
      "steps": [
        "Open cart with the seeded GiftCard item",
        "Proceed to tender",
        "Select Gift Card as the tender type"
      ],
      "test_data": {"Item Type":"GiftCard","Tender":"GiftCard","Currency Format":"USD"},
      "assertions": [
        "System blocks the tender and shows error 'Gift card cannot purchase a gift card'",
        "Order total and tax remain unchanged",
        "No partial tender record is written to the order"
      ]
    }
  ]
}
```

## Final Gate (before stage 5)

Run the gate script immediately after writing both output files:

```bash
python3 references/scripts/validate_test_cases.py \
    --story-id <story_id> \
    --out-dir <out_dir> \
    --expected-count <N>   # omit when scope = single scenario
```

The script checks all structural constraints and exits non-zero on any failure. Full usage, gate-check index, and exit codes: [references/scripts/README.md](./scripts/README.md).

Manual gate checklist (mirrors script checks — for reference only):
- [ ] Both `<story_id>_test-cases.md` and `<story_id>_test-cases.json` exist in `<out_dir>`.
- [ ] `story_id` field in JSON matches the ticket id.
- [ ] `test_cases[]` is non-empty; count matches scope.
- [ ] Every case has `id`, `scenario_id`, `polarity` (Positive/Negative/Boundary), `risk`, `covers[]`.
- [ ] No duplicate `id` values.
- [ ] Every case has ≥1 precondition, ≥1 step, ≥1 assertion.
- [ ] Negative/Boundary cases assert the specific behavior, not a generic failure.
- [ ] MD file contains no embedded ` ```json ` block.
