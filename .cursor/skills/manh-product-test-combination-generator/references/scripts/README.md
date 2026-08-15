# Stage 2 scripts

Python 3 scripts that implement the full Phase 2–6 pipeline. No external dependencies beyond the standard library and the `pict` binary.

---

## Script index

| Script | Phase | Input | Output |
|--------|-------|-------|--------|
| `check_constraint_values.py` | 2 (pre-flight) | factors JSON | exit 0 = clean; exit 1 = mismatches with nearest-match hints |
| `apply_oc_transforms.py` | 5 (loop) | factors JSON + OC flags | mutated factors JSON + optional row count |
| `build_pict_model.py` | 2 + 3 | factors JSON + constraints text | TSV rows printed to stdout |
| `optimize_combinations.py` | 4 | TSV (stdin) + factors JSON | combinations JSON printed to stdout |
| `check_coverage.py` | 4 / 5 | combinations JSON (stdin or file) + factors JSON | coverage table + row counts; exit 1 on MISSING |
| `emit_outputs.py` | 6 | combinations JSON (stdin) + factors JSON | `.md` + `.json` written to `--out-dir` |
| `run_pipeline.py` | 2–6 | factors JSON + optional seeds/prune | `.md` + `.json` written to `--out-dir` |

---

---

## apply_oc_transforms.py

Applies one or more Phase 5 OC optimization mutations to an influencing-factors JSON and
writes a mutated copy. Call this instead of inlining ad-hoc Python during the optimization loop.
After the user confirms an OC check, run this script, inspect the reported row count, then
continue to the next check.

**Supported transforms (may be combined in one call):**

| Flag | OC check | What it does |
|------|----------|--------------|
| `--oc1-collapse-negative FACTOR REP` | OC-1 | Collapse all negatives on FACTOR to one representative value |
| `--oc1-merge-value FACTOR REMOVE KEEP` | OC-1 | Remove one value from FACTOR and subsume it into KEEP |
| `--oc3-drop-factor DROPPED ABSORBING` | OC-3 | Drop DROPPED from factors[]; merge evidence into ABSORBING |
| `--oc5-target FACTOR --oc5-add-constraint "IF..."` | OC-5 | Append a PICT IF/THEN constraint to FACTOR's pict_constraint |
| `--oc8-remove-factor FACTOR` | OC-8 | Remove orthogonal FACTOR from PICT model |
| `--oc8-standalone-rows FACTOR ROW...` | OC-8 | Attach standalone row descriptions to the dropped entry |
| `--oc10-drop-factor FACTOR` | OC-10 | Drop a speculative P2 factor with no explicit plan signal |
| `--seed LABEL KV...` | any | Append a plan-seed (label + FactorName=value pairs) |
| `--run-pipeline` | — | Run build_pict_model + optimize_combinations and print counts |

```bash
python3 references/scripts/apply_oc_transforms.py \
    --factors  <product-repo>/products/<product>/test/POS-180453/POS-180453_influencing-factors.json \
    --out      /tmp/factors_oc1a.json \
    --oc1-collapse-negative \
        "Reprompt Entry Point Condition" \
        "Ineligible line type (return/gift-card/non-merch) -- action hidden" \
    --run-pipeline --story-id POS-180453
# → pict_rows=154  final_rows=163
```

See the full docstring (top of file) for examples of every supported transform.

---

## Typical pipeline

```bash
FACTORS=<product-repo>/products/<product>/test/POS-123456/POS-123456-influencing-factors.json
OUT_DIR=<product-repo>/products/<product>/test/POS-123456
JIRA_ID=POS-123456
CONSTRAINTS=constraints.txt   # optional — plain-text PICT IF/THEN rules, one per line

python3 references/scripts/build_pict_model.py \
    --factors "$FACTORS" \
    --constraints "$CONSTRAINTS" \
  | python3 references/scripts/optimize_combinations.py \
    --factors "$FACTORS" \
    --story-id "$JIRA_ID" \
  | python3 references/scripts/emit_outputs.py \
    --factors "$FACTORS" \
    --story-id "$JIRA_ID" \
    --out-dir "$OUT_DIR"
```

---

## build_pict_model.py

Builds a temp `.pict` model file in memory, runs `pict`, prints TSV to stdout, then deletes the temp file. No `.pict` file is left on disk.

```
usage: build_pict_model.py --factors FACTORS_JSON [--constraints CONSTRAINTS_FILE] [--strength N]

  --factors           Path to stage-1 *-influencing-factors.json
  --constraints       Path to plain-text file of PICT IF/THEN constraint lines (optional)
  --strength          Default PICT strength (default: 2)
```

**Exit codes:** 0 success, 1 error, 2 pict binary not found (install with `brew install pict`).

**Sub-model:** Automatically adds `{ <p0_params> } @ 3` for all P0 factors when ≥2 P0 parameters exist.

---

## optimize_combinations.py

Reads TSV from stdin, applies four optimization passes, prints combinations JSON to stdout.

```
usage: optimize_combinations.py --factors FACTORS_JSON --story-id JIRA_ID
                                 [--seeds-file SEEDS_JSON] [--prune-file PRUNE_JSON]

  --factors           Path to stage-1 *-influencing-factors.json
  --story-id          JIRA ticket id (e.g. POS-123456)
  --seeds-file        JSON array of must-cover row objects {param: value, ...} (optional)
  --prune-file        JSON array of impossible-combo rule objects (optional)
```

**Passes (in order):**
1. Seed — append plan-seed rows from `--seeds-file`, de-duplicate.
2. Prune — remove rows matching any rule in `--prune-file`.
3. Risk-weight — tag each row `high` / `med` / `low` based on P0/P1/P2 Negative/Boundary presence.
4. Coverage-patch — scan for any factor value with zero coverage; add a minimal row to cover it.

**Seeds file format:**
```json
[
  {"source": "plan-seed", "values": {"Feature Flag State": "FF=disabled", "Order Line Type": "Return line"}}
]
```
Unspecified parameters in a seed row are filled with their first (positive) value.

**Prune file format:**
```json
[
  {"reason": "Gift-card popup hidden; internal void impossible",
   "match": {"Order Line Type": "Gift-card line", "Apply Change Set": "Internal-void replacement"}}
]
```
A row is pruned when ALL fields in `match` equal the row values.

---

## check_coverage.py

Reads combinations JSON from stdin (or `--combinations` file), verifies every factor value
appears in at least one combination, and prints a per-value coverage table. Use after
`optimize_combinations.py` during Phase 4/5 to confirm no value was inadvertently dropped.
Exits non-zero if any value has zero coverage.

```
usage: check_coverage.py --factors FACTORS_JSON [--combinations COMBINATIONS_JSON]

  --factors           Path to stage-1 *-influencing-factors.json
  --combinations      Path to saved combinations JSON (optional; default: read from stdin)
```

**Exit codes:** 0 all values covered, 1 one or more values have zero coverage.

**Pipe form (after optimize_combinations.py):**
```bash
python3 references/scripts/build_pict_model.py --factors "$FACTORS" \
  | python3 references/scripts/optimize_combinations.py --factors "$FACTORS" --story-id "$JIRA_ID" \
  | python3 references/scripts/check_coverage.py --factors "$FACTORS"
```

**File form (from saved combinations JSON):**
```bash
python3 references/scripts/check_coverage.py \
    --factors "$FACTORS" \
    --combinations "$OUT_DIR/${JIRA_ID}_test-combinations.json"
```

**Sample output:**
```
pict_rows:  42
final_rows: 49

--- value coverage check ---
  [OK] Reprompt Gating = 'FF=enabled + reprompt-grant=present'  (26 rows)
  [OK] Reprompt Gating = 'FF=disabled'  (22 rows)
  [MISSING] Network State = 'Fluctuating'  (0 rows)
  ...

by source: {'pict': 42, 'plan-seed': 7}

ERROR: the following values have zero coverage:
  MISSING  Network State = 'Fluctuating'
```

---

## emit_outputs.py

Reads combinations JSON from stdin, writes two files.

```
usage: emit_outputs.py --factors FACTORS_JSON --story-id JIRA_ID --out-dir OUT_DIR

  --factors           Path to stage-1 *-influencing-factors.json
  --story-id          JIRA ticket id (e.g. POS-123456)
  --out-dir           Directory to write outputs into (created if absent)
```

**Outputs:**
- `<out-dir>/<story-id>-test-combinations.md` — Optimization Summary + Combinations table. No JSON block.
- `<out-dir>/<story-id>-test-combinations.json` — Full machine-readable JSON per output-format.md schema.

**Final gate checks** are run before writing; script exits non-zero and prints the failing check if any gate fails.
