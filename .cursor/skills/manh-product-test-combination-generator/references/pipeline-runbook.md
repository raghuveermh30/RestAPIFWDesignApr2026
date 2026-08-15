# Stage 2 Pipeline Runbook

Reference for all scripts used in the manh-product-test-combination-generator skill.
Covers invocation patterns, optimization decisions, and the lessons learned from POS-180453.

---

## Script index

| Script | Role | When to use |
|--------|------|-------------|
| `scripts/run_pipeline.py` | **Single-command full pipeline** — build model, run pict, optimize, emit | Default: start here every session |
| `scripts/build_pict_model.py` | Phase 2+3 only — PICT model + TSV to stdout | When you need raw TSV for manual inspection |
| `scripts/optimize_combinations.py` | Phase 4 only — TSV in → combinations JSON out | When piping from a custom model builder |
| `scripts/emit_outputs.py` | Phase 6 only — combinations JSON in → .md + .json files | When replaying a saved combinations JSON |

---

## Standard run (all phases, one command)

```bash
FACTORS="<product-repo>/products/<product>/test/POS-123456/POS-123456_influencing-factors.json"
OUT_DIR="<product-repo>/products/<product>/test/POS-123456"
JIRA_ID="POS-123456"

python3 references/scripts/run_pipeline.py \
    --factors  "$FACTORS" \
    --story-id "$JIRA_ID" \
    --out-dir  "$OUT_DIR"
```

With a constraints file (recommended when OC checks have fired):

```bash
python3 references/scripts/run_pipeline.py \
    --factors      "$FACTORS" \
    --story-id     "$JIRA_ID" \
    --out-dir      "$OUT_DIR" \
    --constraints  constraints.txt \
    --no-submodel
```

---

## run_pipeline.py — full reference

```
usage: run_pipeline.py
    --factors    PATH      stage-1 *_influencing-factors.json  (required)
    --story-id   ID        JIRA id, e.g. POS-180453            (required)
    --out-dir    PATH      directory to write .md + .json       (required)
    --constraints FILE     extra PICT IF/THEN rules, one per line (optional)
    --seeds      FILE      additional seeds JSON array           (optional)
    --prune      FILE      prune rules JSON array                (optional)
    --no-submodel          disable P0 @ 3 sub-model; use pairwise only
    --strength   N         base PICT strength (default: 2)
```

**What it does (in order):**

1. Loads `factors[]` + `plan_seeds[]` from the stage-1 JSON.
2. Merges inline `pict_constraint` fields from every factor with any `--constraints` file.
3. Builds the PICT model text: one `Factor: v1, v2` line per factor, then the sub-model directive (unless `--no-submodel`), then all constraints.
4. Runs the `pict` binary with the model in a temp file (deleted immediately after).
5. Seeds plan_seeds rows; prunes impossible combos; risk-weights; coverage-patches.
6. Runs final gate (all factor values covered); writes `.md` and `.json`.

**Exit codes:** 0 success · 1 error · 2 pict not found (`brew install pict`)

---

## Three-script pipeline (original, for piping/inspection)

```bash
FACTORS="..."
SCRIPTS="references/scripts"
JIRA_ID="POS-123456"
OUT_DIR="..."

python3 "$SCRIPTS/build_pict_model.py" \
    --factors     "$FACTORS" \
    --constraints constraints.txt \
  | python3 "$SCRIPTS/optimize_combinations.py" \
    --factors     "$FACTORS" \
    --story-id    "$JIRA_ID" \
  | python3 "$SCRIPTS/emit_outputs.py" \
    --factors     "$FACTORS" \
    --story-id    "$JIRA_ID" \
    --out-dir     "$OUT_DIR"
```

Redirect stderr to see diagnostic output:

```bash
python3 "$SCRIPTS/build_pict_model.py" --factors "$FACTORS" 2>build.log | ...
```

---

## Constraints file format

Plain text, one PICT `IF/THEN` rule per line. Lines starting with `#` are ignored.

```
# Impossible: offline fetch error can only occur when network is offline
IF [Error / Info Path] = "Offline fetch error (cannot evaluate)" THEN [Network State] = "Offline";

# Gating: grant absent means popup flow is blocked — pin flow factors to defaults
IF [Grant State] = "Reprompt grant absent" THEN [Internal Void Scenario] = "No prior committed items (fresh selection)";
IF [Grant State] = "Reprompt grant absent" THEN [Cancel Prior State] = "Fresh state (nothing changed)";
IF [Grant State] = "Reprompt grant absent" THEN [App Lifecycle During Apply] = "Normal completion (no interruption)";
IF [Grant State] = "Reprompt grant absent" THEN [Error / Info Path] = "No error (happy path)";
```

**Syntax rules (PICT requirement):**
- Factor names inside `[...]` — use exact sanitized name (special chars replaced per sanitize rules).
- Values always in **double-quotes**.
- Operators: `=` (equals) · `<>` (not equals).
- Each rule ends with `;`.

**Sanitize rules** (applied to factor names and values before writing the PICT model):

| Input | Output |
|-------|--------|
| `"` (double-quote) | `'` (single-quote) |
| `–` (en-dash U+2013) | `-` |
| `—` (em-dash U+2014) | `-` |
| `'` (right single quote U+2019) | `'` |
| `→` (U+2192) | `->` |
| `é` (U+00E9) | `e` |
| `à` (U+00E0) | `a` |
| any other non-ASCII | `_` |

Constraint strings in the factors JSON (`pict_constraint` field) preserve double-quotes and are normalized for unicode only — they are **not** passed through the sanitize function. This is intentional: the stage-1 skill writes them in valid PICT syntax already.

---

## Seeds file format

A JSON array. Each entry fills unspecified factors with their first positive value.

```json
[
  {
    "source": "plan-seed",
    "label": "FF disabled — action blocked",
    "note": "OC-9: one explicit FF=off row covers all gating variants.",
    "values": {
      "Grant State": "Reprompt grant present",
      "Network State": "Online",
      "Line Type Eligibility": "Eligible sale line"
    }
  }
]
```

Seeds from `plan_seeds[]` in the stage-1 factors JSON are automatically included — no need to re-specify them in `--seeds` unless adding new ones.

---

## Prune rules file format

A JSON array. A combination row is dropped when **all** fields in `match` equal the row values.

```json
[
  {
    "reason": "Gift-card line: popup hidden, internal void impossible",
    "match": {
      "Line Type Eligibility": "Gift-card line",
      "Internal Void Scenario": "Mixed payload (cancelled + new lines in same request)"
    }
  }
]
```

---

## Optimization decision log — POS-180453

This table records every OC check fired and the outcome. Use it as a template for future sessions.

| Check | Factor | Decision | Before | After |
|-------|--------|----------|--------|-------|
| OC-5 | (new) Offline fetch → Network=Offline | Added constraint; impossible combo eliminated | 190 PICT | 153 PICT |
| OC-5 | (new) Line type excluded/voided → pin flow factors | Added 10 constraints | 153 | 97 (with grant constraints) |
| OC-8 | Reprompt Mode: `Child Remove Item action` | Moved to 2 standalone plan-seeds; removed from PICT | 153 | 98 |
| OC-9 | Grant State: `Child-remove grant absent` | Collapsed to standalone plan-seed; removed from PICT Grant State values (3→2) | 98 | 97 |
| OC-9 | Grant State: `Reprompt grant absent` — pin flow factors | 5 constraints added; grant-absent rows now minimal | 97 | 97 (PICT floor) |
| Strength | P0 sub-model @ 3 → dropped; `--no-submodel` | Sub-model drove 97 rows; dropped to 28 at strength 2; constraints protect key combos | 97+6 seeds=103 | **28+6=34** |

**Key insight:** The P0 strength-3 sub-model is the biggest row multiplier. When ≥3 P0 factors each have ≥3 values, the sub-model can generate 60–100+ rows on its own. Evaluate it first: if constraints already eliminate the dangerous impossible P0 triples, drop the sub-model with `--no-submodel` and accept pairwise P0 coverage.

---

## Count estimators

Use these before running PICT to predict row count and decide whether to add `--no-submodel`:

```
COMBO_EST (pairwise baseline) = max_values × second_max_values × 1.5
```

```
P0_SUBMODEL_EST = product of all P0 value counts at strength 3
                  ≈ (v1 × v2 × v3) / compression_factor   (compression ~2–4x)
```

Example — POS-180453 before optimization:
- P0 factors: Grant(3) × Network(4) × OrderCtx(3) × RepromptMode(3) → 108 triples ÷ ~2 = ~54 sub-model rows alone
- P1 factors add pairwise on top → total ~190 rows

After OC-8 + OC-9 + `--no-submodel`:
- P0 factors: Grant(2) × Network(4) × OrderCtx(3) × RepromptMode(2) at strength 2
- COMBO_EST = 4 × 3 × 1.5 = 18; actual = 28 PICT rows

---

## Quick reference — when to use each flag

| Situation | Flag / action |
|-----------|--------------|
| First run, no constraints yet | `run_pipeline.py` with no extra flags |
| Row count > 70, P0 sub-model is driver | Add `--no-submodel` |
| Impossible combos in model (OC-5) | Add IF/THEN rules to `constraints.txt`, pass `--constraints constraints.txt` |
| Gating factors inflate rows (OC-9) | Add constraints that pin non-primary factors when gating value is set |
| Orthogonal feature in Reprompt Mode (OC-8) | Remove value from `factors[]` in JSON; add plan_seeds; re-run |
| Must-cover scenarios not in PICT output | Add entries to `plan_seeds[]` in stage-1 JSON; they auto-load |
| Row still > 70 after all checks | Use `--strength 1` as last resort (covers every individual value, not pairs) |

---

## Inline script pattern (deprecated — use run_pipeline.py instead)

During the POS-180453 session, the pipeline was invoked with inline heredoc scripts to test intermediate states. These are now consolidated into `run_pipeline.py`. The key pattern those scripts implemented:

```python
# 1. Sanitize factor names and values for PICT
def sanitize(name):
    name = name.replace('"', "'").replace("\n", " ").strip()
    for old, new in [("\u2013","-"), ("\u2014","-"), ("\u2019","'"),
                     ("\u2192","->"), ("\u00e9","e"), ("\u00e0","a")]:
        name = name.replace(old, new)
    return "".join(c if ord(c) < 128 else "_" for c in name)

# 2. Build model lines
lines = [f'{sanitize(fac["factor"])}: {", ".join(sanitize(v) for v in fac["values"])}' 
         for fac in factors]

# 3. Add sub-model (or skip with no_submodel flag)
p0 = [sanitize(f["factor"]) for f in factors if f.get("priority") == "P0"]
if not no_submodel and len(p0) >= 2:
    lines.append(f'{{ {", ".join(p0)} }} @ 3')

# 4. Add constraints — pict_constraint fields use raw (double-quoted); extra use sanitize_constraint()
for fac in factors:
    if raw := fac.get("pict_constraint", "").strip():
        lines.append(sanitize_constraint(raw))  # unicode-normalize, keep double-quotes

# 5. Run pict via temp file, delete temp file after
with tempfile.NamedTemporaryFile(mode="w", suffix=".pict", delete=False, encoding="utf-8") as tmp:
    tmp.write("\n".join(lines))
result = subprocess.run([pict_bin, tmp.name, "/o:2"], capture_output=True, text=True)
os.unlink(tmp.name)
```

The critical difference between the `pict_constraint` inline field and extra constraints:
- **`pict_constraint` field** — already written with double-quotes by stage 1; pass through `sanitize_constraint()` (unicode normalize only, preserves `"`).
- **Extra constraints file** — written by the agent; also use double-quotes in the file directly.
- **Never call `sanitize()` on constraint strings** — it converts `"` to `'`, breaking PICT syntax.
