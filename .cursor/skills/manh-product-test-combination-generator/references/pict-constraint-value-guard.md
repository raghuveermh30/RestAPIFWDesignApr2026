# PICT constraint value-mismatch guard

## The bug

PICT constraint matching is **exact string match** against parameter values in the `.pict` model.
If the string in a `pict_constraint` `IF/THEN` clause does not exactly match the value as
written in the `factors[].values[]` array, PICT silently ignores the constraint and emits
rows that violate it.

### Observed failure — OM-122824

The `Gateway Response Outcome` factor's `pict_constraint` field contained:

```
IF [Request Validation] = "Validation failure" THEN [Gateway Response Outcome] <> "Approved"; ...
```

The actual value in `factors[].values[]` was:

```
"Validation failure (null/blank/invalid field)"
```

Because `"Validation failure"` ≠ `"Validation failure (null/blank/invalid field)"`, PICT
did not apply the constraint. The raw TSV output contained the impossible row:

```
Supported gateway ID | Approved | Validation failure (null/blank/invalid field) | User exit returns null
```

That combination is unreachable in production: a validation failure always throws before
the gateway is called, so `Approved` is impossible when validation fails.

---

## When to check

Run this validation **at the start of Phase 2**, before calling `build_pict_model.py`,
whenever the factors JSON contains any `pict_constraint` fields. Re-run after every Phase 5
OC mutation that touches values or adds constraints.

---

## How to detect

Run `references/scripts/check_constraint_values.py` against the stage-1 factors JSON:

```bash
python3 references/scripts/check_constraint_values.py --factors "$FACTORS"
```

Exit code 0 = clean. Exit code 1 = mismatches found (script prints the offending factor,
the unmatched token, and the nearest matching value from `factors[].values[]`).

---

## How to fix

**In the stage-1 factors JSON**, update the `pict_constraint` field on the offending factor
so the quoted value token matches the exact string in `values[]` character-for-character.

### Example fix — OM-122824

Before (broken):
```json
"pict_constraint": "IF [Request Validation] = \"Validation failure\" THEN [Gateway Response Outcome] <> \"Approved\"; ..."
```

After (correct):
```json
"pict_constraint": "IF [Request Validation] = \"Validation failure (null/blank/invalid field)\" THEN [Gateway Response Outcome] <> \"Approved\"; ..."
```

Edit the factors JSON in-place, re-run `check_constraint_values.py` to confirm it passes,
then proceed to `build_pict_model.py`.

---

## Root-cause pattern

Stage-1 (`manh-product-influencing-factor-identifier`) writes `pict_constraint` strings at
the time the constraint is drafted — often before the final `values[]` label is confirmed.
If the value label is later refined or parenthetical detail is added, the constraint string
can lag behind.

Common triggers:
- A short label is used in the constraint, but the final value has a clarifying parenthetical:
  `"Validation failure"` → `"Validation failure (null/blank/invalid field)"`
- A value is renamed during the grooming session but the constraint is not updated.
- A constraint is copied from an earlier story and its value strings are not updated.

---

## Skill instruction (Phase 2 gate)

Before calling `build_pict_model.py`, the skill **must**:

1. Run `python3 references/scripts/check_constraint_values.py --factors <factors_path>`.
2. If exit code 1: **stop**, show the printed mismatches to the user, ask them to confirm
   the corrected value string, edit the `pict_constraint` field in the factors JSON in-place,
   then re-run the script until it exits 0.
3. Only proceed to `build_pict_model.py` when `check_constraint_values.py` exits 0.

Never inline ad-hoc constraint-checking code — always use the script.
