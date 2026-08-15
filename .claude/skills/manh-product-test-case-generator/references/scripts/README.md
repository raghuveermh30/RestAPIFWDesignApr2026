# Stage 4 scripts

One Python 3 script that validates the two output files written by `manh-product-test-case-generator`. No external dependencies beyond the standard library.

---

## Script index

| Script | Purpose | Input | Output |
|---|---|---|---|
| `validate_test_cases.py` | Final-gate checker — verifies `<story_id>_test-cases.json` and `<story_id>_test-cases.md` satisfy every constraint in `output-format.md` | `--story-id`, `--out-dir`, optional `--expected-count` | Pass/fail summary printed to stdout; exit code 0 (pass) or 1 (fail) |

---

## validate_test_cases.py

Runs all final-gate checks from `references/output-format.md` and exits non-zero if any check fails. Run this immediately after writing both output files — before telling the user to proceed to stage 5.

```
usage: validate_test_cases.py --story-id STORY_ID --out-dir OUT_DIR
                               [--expected-count N]

  --story-id        JIRA ticket id used as the file name prefix (e.g. OM-122824)
  --out-dir         Directory containing <story_id>_test-cases.md and
                    <story_id>_test-cases.json
  --expected-count  Exact number of test cases expected. Omit when scope is a
                    single scenario (count check is skipped).
```

**Exit codes:** 0 pass · 1 one or more gate checks failed · 2 required file unreadable

**Example — full suite (21 expected cases):**
```bash
python3 references/scripts/validate_test_cases.py \
    --story-id OM-122824 \
    --out-dir products/oms/test/OM-122824 \
    --expected-count 21
```

**Example — single-scenario run (no count check):**
```bash
python3 references/scripts/validate_test_cases.py \
    --story-id OM-122824 \
    --out-dir products/oms/test/OM-122824
```

**Successful output:**
```
Test cases : 21
Polarities : {'Positive': 5, 'Negative': 9, 'Boundary': 7}

All final gate checks passed ✅
```

**Failure output:**
```
FAIL: TC-S003: 'assertions' is missing or empty
FAIL: MD file contains an embedded JSON code block (forbidden by output-format.md)
```

---

## Gate checks performed

| # | Check | Source rule |
|---|---|---|
| 1 | Both `<story_id>_test-cases.json` and `<story_id>_test-cases.md` exist in `--out-dir` | output-format.md §Both files |
| 2 | `story_id` field in JSON matches `--story-id` argument | output-format.md §MACHINE_BLOCK |
| 3 | `test_cases[]` is non-empty | output-format.md §Final Gate |
| 4 | Count equals `--expected-count` when provided | output-format.md §Final Gate |
| 5 | No duplicate `id` values across cases | output-format.md §Final Gate |
| 6 | Every case has `id`, `scenario_id`, `polarity`, `risk` (non-empty strings) | output-format.md §MACHINE_BLOCK |
| 7 | `covers` is present and is a list | output-format.md §MACHINE_BLOCK |
| 8 | `polarity` is one of `Positive`, `Negative`, `Boundary` | output-format.md §MACHINE_BLOCK |
| 9 | `risk` is one of `high`, `med`, `low` | output-format.md §MACHINE_BLOCK |
| 10 | Every case has ≥1 `preconditions`, ≥1 `steps`, ≥1 `assertions` | output-format.md §Final Gate |
| 11 | MD file contains no ` ```json ` code block | output-format.md §Final Gate |
| 12 | MD file is non-empty | output-format.md §Both files |
