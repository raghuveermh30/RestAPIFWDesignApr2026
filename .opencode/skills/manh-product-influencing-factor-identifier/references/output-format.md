# Stage 1 output format

Write **two separate files** to `<out_dir>` resolved in Phase 0 (`{workspace}/{product-repo}/products/<product>/test/<jira_id>/`; create the directory if absent):

| File | Purpose |
|---|---|
| `<jira_id>_influencing-factors.md` | Human-readable artifact — features, factor table, gaps note. **No JSON block.** |
| `<jira_id>_influencing-factors.json` | Machine-readable artifact — full structured data for downstream stages. |

---

## MD artifact layout (`<jira_id>_influencing-factors.md`)

1. `## Features` — the derived feature label(s) as a bullet list.
2. `## Influencing Factors` — human table reflecting the **user-approved** `FINAL_FACTORS`:
   `Group | Priority | Factor | Values | Pos/Neg/Boundary | Confidence | Source | Evidence`.
3. `## Fixed Context` — single-value factors that are test preconditions (not PICT parameters), as a bullet list: `Factor: value — reason`.
4. `## Estimate Summary` — one paragraph with the three numbers:
   - `COMBO_EST ≈ N rows` (core PICT matrix)
   - `Seeds: S` (plan_seeds[] count)
   - `Side surfaces: X tests` (OC-8 standalone rows total)
   - `Practical total: T tests` (COMBO_EST + S + X)
   - `Budget: COMBO_BUDGET` (resolved value, not the default label)
5. `## Gaps` — candidate new groups for the index (with suggested tier), or "None."

> Do **not** include any JSON block in this file.

---

## JSON artifact schema (`<jira_id>_influencing-factors.json`)

```json
{
  "story_id": "<jira_id>",
  "product": "<product>",
  "features": ["<Feature A derived from plan>", "<Feature B derived from plan>"],
  "combo_est": 24,
  "combo_budget": 60,
  "practical_total": {
    "core_matrix": 24,
    "seeds": 8,
    "side_surfaces": 4,
    "total": 36
  },
  "groups_considered": [
    {"group": "<Group name from catalog>", "priority": "P0", "selected": true},
    {"group": "<Group name from catalog>", "priority": "P0", "selected": false, "reason": "<Why this P0 group is excluded — cite plan evidence>"},
    {"group": "<Group name from catalog>", "priority": "P2", "selected": false, "reason": "<Why this P2 group is excluded — plan does not mention it>"}
  ],
  "user_finalized": true,
  "fixed_context": [
    {"factor": "<Factor name>", "value": "<Single value>", "reason": "<Why single-value; cite plan or user confirmation>"}
  ],
  "factors": [
    {
      "group": "<Group name from catalog>", "priority": "P0", "factor": "<Concrete factor name>",
      "values": ["<value A>", "<value B>"],
      "positive": ["<value A>"], "negative": ["<value B>"], "boundary": [],
      "confidence": "high", "source": "derived",
      "evidence": "<Plan section or file reference — one line>",
      "pict_constraint": ""
    },
    {
      "group": "<Group name from catalog>", "priority": "P0", "factor": "<Concrete factor name>",
      "values": ["<value X>", "<value Y>", "<value Z>"],
      "positive": ["<value X>"], "negative": ["<value Y>"], "boundary": ["<value Z>"],
      "confidence": "high", "source": "derived",
      "evidence": "<Plan section or file reference — one line>",
      "pict_constraint": "IF [<Factor name>] = \"<value>\" THEN [<Other factor>] <> \"<impossible value>\";"
    }
  ],
  "dropped_factors": [
    {
      "group": "<Group name from catalog>",
      "factor": "<Factor name>",
      "reason": "<OC check ID + one-line rationale>",
      "assertions_moved_to": ["<Factor name where assertion was added>"]
    },
    {
      "group": "<Group name from catalog>",
      "factor": "<Factor name>",
      "reason": "<OC check ID + one-line rationale>",
      "standalone_rows": [
        "<Explicit test row description 1>",
        "<Explicit test row description 2>"
      ]
    }
  ],
  "guard_invariant": [
    {
      "factor": "<Factor name>",
      "collapsed_to": "<Representative value>",
      "reason": "<Plan reference proving no branching on this factor>"
    }
  ],
  "plan_seeds": [
    {
      "source": "plan-seed",
      "label": "<Concise test scenario name>",
      "note": "<Why this seed exists — OC check reference or plan section>",
      "values": {
        "<Factor name>": "<value>",
        "<Factor name>": "<value>"
      }
    }
  ],
  "gaps": [],
  "open_questions_resolved": [
    {"id": "OQ-1", "question": "<Question text>", "resolution": "<Resolution — cite plan or user answer>"}
  ],
  "quality_recovery": [
    {
      "source": "OC-drop | gap-rule | P2-nonfunctional",
      "trigger": "<OC-N drop / G1–G5 / P2 group name>",
      "behavioral_gap": "<One sentence: what the tester cannot verify from PICT combinations alone>",
      "recovery": "seed | assertion | deferred",
      "seed_label": "<Label of the plan_seed that covers this gap — or empty if assertion/deferred>",
      "deferred_reason": "<Why deferred — only when recovery=deferred>"
    }
  ]
}
```

### Field rules

**Top-level fields:**
- `story_id` — the `<jira_id>` derived in Phase 0.
- `combo_est` — integer; the `COMBO_EST` value computed in Phase 4c Step 1 (`max_values × second_max_values × 1.5`, rounded). Stage 2 reads this to display context.
- `combo_budget` — integer; the `COMBO_BUDGET` resolved in Phase 4c Step 1. Read from the plan's existing JSON if present; otherwise derived from factor count (40 / 60 / 80). Never hardcode 70.
- `practical_total` — object with four integer fields: `core_matrix` (= `combo_est`), `seeds` (= `plan_seeds[]` count), `side_surfaces` (= total standalone rows from OC-8 drops), `total` (= sum). Stage 2 surfaces these in its output header.
- `groups_considered` — every group from the CATALOG; `selected: false` entries MUST have a `reason`. Stage 2 uses this for audit only.
- `user_finalized` — **must be `true`** after Phase 5 sign-off. Stage 2 script rejects the file if this is not `true`.
- `fixed_context[]` — single-value factors not in PICT. Each entry: `{factor, value, reason}`. Stage 2 prints these as test precondition notes.

**`factors[]` field rules:**
- `factors[].values` — **flat array of strings** (not objects). Stage 2 reads these directly as PICT parameter values.
- `factors[].positive/negative/boundary` — arrays of value strings from `values[]`; each value must appear in exactly one classification array. Every factor must have ≥1 positive value OR an explicit note in `evidence`.
- `factors[].source` — `"derived"` (from plan/code) or `"user"` (user-added in Phase 5) or `"catalog"`.
- `factors[].pict_constraint` — optional PICT constraint string for this factor's values. **Must use double-quoted value strings** (PICT syntax): `IF [Factor] = "value" THEN [OtherFactor] <> "impossible";`. Empty string if no constraint. Stage 2 injects this directly into the PICT model file.

**`dropped_factors[]` field rules:**
- One entry per factor removed in Phase 4b or Phase 4c (OC checks). Always include `group`, `factor`, `reason`.
- For OC-8 orthogonal removals: include `standalone_rows[]` — a list of explicit test row descriptions for the standalone test.
- For OC-6 log removals: include `assertions_moved_to[]` — the factors whose `evidence` received the log assertions.

**`guard_invariant[]` field rules:**
- One entry per factor the plan proves does not affect the changed outcome, collapsed to a single representative value. Include `factor`, `collapsed_to`, `reason` (cite the plan line/section). Stage 2 propagates this array directly into its JSON output.

**`plan_seeds[]` field rules:**
- One entry per must-cover row that PICT alone cannot guarantee (gating-baseline rows, boundary edges from dropped factors, gap-rule scenarios, P2 non-functional obligations, explicit AC scenarios). Format: `{source, label, note, values: {FactorName: value, ...}}`. Unspecified factors are filled with their first positive value by Stage 2.
- `label` — **behavioral assertion in present tense**, describing what the tester verifies. Do not embed factor names, PICT IDs, or enum values in the label. ✅ `"Action hidden when feature flag disabled"` · ❌ `"FF=off + Grant=present test"`.
- `note` — one sentence: which OC check / gap rule / plan section requires this seed and why it cannot come from PICT alone.
- `values` — **sparse**: specify only the 1–3 factors whose non-default value makes this seed distinct. Omit every factor whose first positive value is correct for this scenario. Stage 2 fills the rest automatically. **Never enumerate all factors in a seed** — that hardcodes the model to one epic and defeats Stage 2's gap-filling logic.

**`quality_recovery[]` field rules:**
- One entry per gap identified in Phase 4d (OC drops, gap rules G1–G5, excluded P2 non-functional concerns).
- `source` — one of: `"OC-drop"` (from an OC check), `"gap-rule"` (G1–G5), `"P2-nonfunctional"` (excluded P2 group with behavioral obligation).
- `trigger` — the specific OC check ID (e.g. `"OC-8"`), gap rule ID (e.g. `"G2"`), or P2 group name (e.g. `"Observability & Logging"`).
- `behavioral_gap` — one sentence stating what a tester cannot verify from PICT combinations alone.
- `recovery` — one of: `"seed"` (a plan_seeds[] entry covers it), `"assertion"` (added to a factor's evidence string), `"deferred"` (explicitly deferred with a reason).
- `seed_label` — the exact `label` of the covering plan_seed, or empty string if recovery=assertion/deferred.
- `deferred_reason` — required when recovery=deferred; must cite why the gap is acceptable to defer.

**`open_questions_resolved[]`:**
- All `OPEN_QUESTIONS` surfaced in Phase 4/5 with their resolution. May be empty array.

---

## Final Gate (must pass before stage 2)

Run the validator script after writing the JSON artifact:

```bash
python3 references/validate-output.py <path-to-influencing-factors.json>
```

The script exits `0` (ALL PASS) or `1` (FAILURES DETECTED) and prints one `[OK]` / `[FAIL]` line per check. Do not proceed to Stage 2 until exit code is `0`.

Checks covered by the script (subset of the full gate — run manually for the remainder):
- `combo_est`, `combo_budget`, and `practical_total` fields are present and non-zero
- `practical_total.total == core_matrix + seeds + side_surfaces`
- `combo_budget >= combo_est`
- At least one feature in `features[]`
- `factors[]` non-empty and `user_finalized` is `true`
- No single-value factor in `factors[]`
- All factors have `evidence` and a P0/P1/P2 `priority`
- All `pict_constraint` strings contain double-quoted values
- All `plan_seeds[]` entries have sparse `values` maps (≤3 factors)
- All `dropped_factors[]` entries have a `reason`
- No `quality_recovery[]` entry with `recovery: "deferred"` is missing `deferred_reason`

Manual checks (not automated — verify by inspection):
- [ ] `combo_est`, `combo_budget`, and `practical_total` fields are present and non-zero.
- [ ] `combo_budget` matches the value resolved in Phase 4c (not a default 70).
- [ ] At least one feature in `features[]`.
- [ ] Every factor derived from plan/code evidence (has an `evidence` string) or user-added (`source: user`).
- [ ] Every factor carries a P0/P1/P2 `priority` (inherited from its group, or assigned for user-added).
- [ ] Every P0 group is selected (≥1 factor) or has an explicit exclusion `reason` in `groups_considered` — never silently dropped.
- [ ] Every factor has enumerated `values` as a **flat string array**, a Pos/Neg/Boundary classification, a confidence, and a source.
- [ ] No single-value factor exists in `factors[]` — single-value factors are in `fixed_context[]` only.
- [ ] No derived-outcome factor (per rule E8) exists in `factors[]` — outcomes of other factors are assertions, not parameters.
- [ ] No two factors in `factors[]` have the same values derived from the same plan evidence — cross-group dedup check passed (Phase 3 + OC-10).
- [ ] All `pict_constraint` strings use double-quoted value syntax (PICT requirement).
- [ ] All factors removed by Phase 4c OC checks are recorded in `dropped_factors[]` with `reason`.
- [ ] Every OC drop, gap-rule trigger, and excluded P2 non-functional concern is recorded in `quality_recovery[]` with `recovery` status.
- [ ] No `quality_recovery[]` entry has `recovery: "deferred"` without a `deferred_reason`.
- [ ] Every `plan_seeds[]` entry has a sparse `values` map (≤3 factors specified); no seed enumerates all factors.
- [ ] Every `plan_seeds[]` `label` is a behavioral assertion in present tense, not a test-case title or factor enumeration.
- [ ] All must-cover rows from dropped factors, gap rules, and gating-baseline rows are in `plan_seeds[]`.
- [ ] `guard_invariant[]` lists every factor collapsed to a single representative value with a justifying plan reference.
- [ ] `user_finalized` is `true` (Phase 5 gate passed).
- [ ] JSON file is valid JSON and `factors[]` is non-empty.
- [ ] MD file contains no JSON blocks.
