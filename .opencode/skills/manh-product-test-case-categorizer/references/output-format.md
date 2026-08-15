# Stage 5 output format

## Output target — overwrite in place when possible
- Compute the target path `<out_dir>/<story_id>_test-cases.md` (where `<out_dir>` = `{workspace}/{product-repo}/products/<product>/test/<story_id>`).
- If a file already exists at that path (the stage-4 `<story_id>_test-cases.md` — typically exactly the `<test_cases_path>` read in Phase 0), **OVERWRITE it in place** with the categorized output (a superset of stage-4: same cases, redundant ones removed, each survivor stamped with `Category`).
- If no such file exists at `<out_dir>`, **create** `<out_dir>/<story_id>_test-cases.md` new.
- Do not emit a separate `*-test-cases-final.md`; the categorized set replaces/becomes the canonical `<story_id>_test-cases.md`.

## Artifact layout — same shape as stage 4 (do NOT reduce to a metadata table)
1. A short **summary** header: counts before/after, dropped count, distribution by category.
2. The **full test-case sections** (identical layout to stage 4) — each beginning with its `Category:` and `Repo:` lines — for every surviving case, including merged cases (which carry the union of assertions from Phase 2). Sections: ID, Scenario, **Category**, **Repo**, Polarity, Risk, Preconditions, Steps, Test Data, Assertions.
3. A **dropped log**: ID | reason | subsumed-by/merged-by.
4. The MACHINE_BLOCK below.

The MACHINE_BLOCK `test_cases[]` objects are **identical to stage 4** (full preconditions/steps/test_data/assertions) with two added attributes: `category` and `repo_name` (plus optional `framework`/`target_path`/`automation_candidate`). No stage-4 field is removed.

```json
{
  "story_id": "<story_id>",
  "summary": {"in": 22, "final": 17, "dropped": 5,
    "by_category": {"Unit": 7, "Component": 6, "Integration": 3, "E2E": 1}},
  "test_cases": [
    {
      "id": "TC-S014",
      "scenario_id": "S014",
      "covers": ["C002"],
      "category": "Component",
      "repo_name": "active-store",
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
      ],
      "framework": "Jest",
      "automation_candidate": "yes"
    }
  ],
  "dropped": [
    {"id":"TC-S011","reason":"merged-observation-layer","subsumed_by":"TC-S009"}
  ]
}
```

## Plan update — write back artifact path after file is written

After the categorized test-cases file is successfully written to `<out_dir>`, update the input plan file (`<plan_path>`) to record the final artifact path.

Locate the test surface section in the plan. Look for a heading matching one of these patterns (case-insensitive):
- `## N) Test surface`, `## Test surface and scenario matrix`, `### Test surface`

If found, append a `### Generated test artifacts` subsection immediately before the closing `---` of that section (or at the end of the section if no `---` follows). If the subsection already exists, overwrite only the `Test cases (categorized)` line within it.

If no test-surface section is found, append the subsection at the very end of the plan file.

The subsection format:

```markdown
### Generated test artifacts

| Stage | File |
|---|---|
| Test cases (categorized) | `{product-repo}/products/{product}/test/<story_id>/<story_id>_test-cases.md` |
```

Where the path is a **workspace-relative reference path** (e.g. `active-order/products/oms/test/OM-122824/OM-122824_test-cases.md`) — not the full absolute filesystem path. Derive it by stripping the `{workspace}/` prefix from `<out_dir>`. This is a **write-back to the plan file** — edit `<plan_path>` in place using the same mechanism used to write the test-cases file. Announce:
> "Plan updated: added generated test artifact path to `<plan_path>`."

---

## Final Gate (chain complete)
- [ ] Output mirrors the stage-4 format: every survivor retains its full preconditions/steps/test_data/assertions; the only added fields are `category` and `repo_name`.
- [ ] Written to `<out_dir>/<story_id>_test-cases.md` (where `<out_dir>` = `{workspace}/{product-repo}/products/{product}/test/<story_id>`) — overwriting the stage-4 file in place if it exists, else created new. No separate `*-test-cases-final.md`.
- [ ] Every survivor has a `category` in {Unit, Component, Integration, E2E} (or a documented level-split into `TC-x.u`/`TC-x.e`, each with its own `category`).
- [ ] Every survivor has a `repo_name` (E2E: confirmed via `askUser()`; Unit/Component/Integration: auto-detected from case content).
- [ ] No surviving pair is redundant by the Phase-2 rules.
- [ ] No two survivors assert the same guarantee at different observation layers — such pairs are merged into one case asserting at both layers, the dropped one logged as `merged-observation-layer`.
- [ ] Every high-risk pair, Negative, and Boundary value is still covered (merged cases carry the union of the dropped case's distinct assertions).
- [ ] Every dropped case has a reason + (if subsumed/merged) a `subsumed_by` id.
- [ ] MACHINE_BLOCK valid; traceability to combinations preserved end-to-end.
- [ ] Plan file updated at `<plan_path>` with `### Generated test artifacts` subsection referencing the categorized test-cases file path.

## Worked example — POS-181833 (safety-net dedup + preserve)
If upstream stages did NOT already merge, this stage must catch the leak.

**MERGE (instrumentation / observation-layer):** TC-S009 ("≤1 performOrderReturn, via Network tab") and TC-S011 ("≤1 performOrderReturn reaches backend, via API log") assert the **same guarantee** at different layers, same `covers` C001–C007. Keep TC-S009, append S011's backend assertion to it, drop S011. Log `{"dropped":"TC-S011","merged_into":"TC-S009","reason":"merged-observation-layer"}`. Polarity labels (Negative vs Boundary) do not protect S011 — it watches the same outcome.

**COLLAPSE (guard-invariant duplicates, if Skill 2 missed them):** TC-S004/S005/S006/S007 all assert "button disabled in-flight → zero performOrderReturn," varying only order type × form factor — factors the plan proves the `scanInQueue` guard is invariant to. Collapse to two (one Desktop, one Mobile); log the rest as `reason: guard-invariant-duplicate`.

**PRESERVE (do NOT drop — genuinely distinct):** TC-S010 (guard is scanInQueue-based, not OrderId-based), TC-S012 (enable-timing the instant scanInQueue returns to 0), TC-S008 (finally-block decrement on processBarcode error), TC-S003 (FF-disabled baseline / no-regression).

`category` for survivors (each keeps full stage-4 detail plus this one field): TC-S010/S012 (state-transition logic) → Component/Unit; TC-S009 (now carrying the backend assertion) → Integration; full rapid-click flow → E2E only if no cheaper layer can observe the duplicate-submit guard.
