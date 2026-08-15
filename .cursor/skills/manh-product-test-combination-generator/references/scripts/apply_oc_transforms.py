#!/usr/bin/env python3
"""
apply_oc_transforms.py — Apply one or more Phase 5 OC optimization transforms
to an influencing-factors JSON and write a mutated copy, then optionally
run the full Phase 2–4 pipeline to report the new row count.

Use this script instead of inlining ad-hoc Python during the optimization loop.
After user confirms an OC check, call this script with the appropriate --oc flag(s),
inspect the reported row count, then continue to the next check.

Usage:
    python3 apply_oc_transforms.py \\
        --factors  <path-to-influencing-factors.json> \\
        --out      <path-to-mutated-output.json> \\
        [--oc1-collapse-negative  "Factor Name" "Representative negative value to keep"] \\
        [--oc1-merge-value        "Factor Name" "Value to remove" "Value to merge into"] \\
        [--oc3-drop-factor        "Factor Name" "Target factor that absorbs the evidence"] \\
        [--oc5-add-constraint     "IF [FactorA] = \\"val\\" THEN [FactorB] = \\"val\\";"] \\
        [--oc8-remove-factor      "Factor Name"] \\
        [--oc8-standalone-rows    "Factor Name" "Row 1 description" "Row 2 description" ...] \\
        [--oc10-drop-factor       "Factor Name"] \\
        [--seed                   "label" "FactorName=value" "FactorName2=value2" ...] \\
        [--run-pipeline] \\
        [--story-id JIRA_ID]

Flags:
  --oc1-collapse-negative  Factor  RepresentativeNeg
        Collapse all values in factor["negative"] to a single representative.
        factor["values"] is rebuilt as positive[] + [representative].

  --oc1-merge-value  Factor  ValueToRemove  ValueToKeep
        Remove one value from factor["values"] and factor["negative"]
        (the removed value is noted as a sub-scenario of ValueToKeep).

  --oc3-drop-factor  DroppedFactor  AbsorbingFactor
        Drop DroppedFactor from factors[]; append an OC-3 note to AbsorbingFactor's
        evidence; record in dropped_factors[].

  --oc5-add-constraint  "IF [FactorA] = \\"val\\" THEN [FactorB] = \\"val\\";"
        Append a PICT constraint string to the named factor's pict_constraint field.
        Pass the raw PICT IF/THEN line as a single quoted argument.
        Use --oc5-target to specify which factor receives the constraint (default: first
        factor whose name appears in the constraint string).

  --oc5-target  "Factor Name"
        Factor to attach the --oc5-add-constraint to (optional, see above).

  --oc8-remove-factor  "Factor Name"
        Remove factor from factors[]; record in dropped_factors[].

  --oc8-standalone-rows  "Factor Name"  "Row 1"  "Row 2" ...
        Attach standalone row descriptions to the OC-8 dropped_factors entry for Factor Name.
        Must be paired with --oc8-remove-factor.

  --oc10-drop-factor  "Factor Name"
        Remove a P2 factor from factors[] when no explicit plan signal exists.
        Records reason in dropped_factors[].

  --seed  "label"  "FactorName=value" ...
        Append a new plan-seed to plan_seeds[].
        label is the first argument; remaining arguments are key=value pairs.

  --run-pipeline
        After writing --out, pipe the mutated file through build_pict_model.py →
        optimize_combinations.py and print pict_rows / final_rows.
        Requires --story-id.

  --story-id  JIRA_ID
        Required when --run-pipeline is set.

Exit codes: 0 success, 1 error, 2 pict not found (only with --run-pipeline).

Examples
--------
# OC-1a: collapse two negatives on Reprompt Entry Point Condition to one representative
python3 apply_oc_transforms.py \\
    --factors  /tmp/factors.json \\
    --out      /tmp/factors_oc1a.json \\
    --oc1-collapse-negative \\
        "Reprompt Entry Point Condition" \\
        "Ineligible line type (return/gift-card/non-merch) -- action hidden" \\
    --run-pipeline --story-id POS-180453

# OC-1b: merge 'API error on popup open' into 'API error on apply commit'
python3 apply_oc_transforms.py \\
    --factors  /tmp/factors_oc1a.json \\
    --out      /tmp/factors_oc1b.json \\
    --oc1-merge-value \\
        "Error / Info Path" \\
        "API error on popup open (data fetch failure)" \\
        "API error on apply commit -- no orphan lines" \\
    --run-pipeline --story-id POS-180453

# OC-3: drop In-Flow App Crash into Error / Info Path
python3 apply_oc_transforms.py \\
    --factors  /tmp/factors_oc1b.json \\
    --out      /tmp/factors_oc3.json \\
    --oc3-drop-factor "In-Flow App Crash" "Error / Info Path" \\
    --run-pipeline --story-id POS-180453

# OC-8: remove Child Remove Item factors as standalone
python3 apply_oc_transforms.py \\
    --factors  /tmp/factors_oc3.json \\
    --out      /tmp/factors_oc8.json \\
    --oc8-remove-factor "Child Remove Item Gating" \\
    --oc8-standalone-rows "Child Remove Item Gating" \\
        "FF enabled + Remove Item grant present + linked child line: Remove Linked Item action visible" \\
        "FF disabled or Remove Item grant absent: Remove Linked Item action hidden" \\
    --oc8-remove-factor "Child Remove Item Entry Point Condition" \\
    --oc8-standalone-rows "Child Remove Item Entry Point Condition" \\
        "Non-child line (no LinkedItemParentLineId): Remove Linked Item action not present" \\
    --run-pipeline --story-id POS-180453

# OC-5: add constraint to eliminate impossible combinations
python3 apply_oc_transforms.py \\
    --factors  /tmp/factors_oc8.json \\
    --out      /tmp/factors_oc5.json \\
    --oc5-target "Reprompt Entry Point Condition" \\
    --oc5-add-constraint \\
        'IF [Reprompt Action Gating] = "FF disabled or grant absent" THEN [Reprompt Entry Point Condition] = "Ineligible line type (return/gift-card/non-merch) -- action hidden";' \\
    --run-pipeline --story-id POS-180453

# OC-10: drop a speculative P2 factor and add a standalone seed instead
python3 apply_oc_transforms.py \\
    --factors  /tmp/factors_oc5.json \\
    --out      /tmp/factors_oc10.json \\
    --oc10-drop-factor "CFD State During Reprompt" \\
    --seed "CFD paired -- popup opens on associate POS, CFD also mirrors the popup correctly" \\
    --run-pipeline --story-id POS-180453
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

SCRIPTS_DIR = Path(__file__).parent


def load(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def save(data: dict, path: str):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def find_factor(factors: list, name: str) -> dict | None:
    for fac in factors:
        if fac["factor"] == name:
            return fac
    return None


def ensure_dropped(data: dict) -> list:
    if "dropped_factors" not in data:
        data["dropped_factors"] = []
    return data["dropped_factors"]


def ensure_plan_seeds(data: dict) -> list:
    if "plan_seeds" not in data:
        data["plan_seeds"] = []
    return data["plan_seeds"]


# ---------------------------------------------------------------------------
# OC transforms
# ---------------------------------------------------------------------------

def oc1_collapse_negative(data: dict, factor_name: str, representative: str):
    fac = find_factor(data["factors"], factor_name)
    if not fac:
        print(f"WARNING: factor '{factor_name}' not found — skipping OC-1 collapse", file=sys.stderr)
        return
    old_neg = list(fac.get("negative", []))
    fac["negative"] = [representative]
    fac["values"] = fac.get("positive", []) + [representative]
    fac.setdefault("_oc_notes", {})["OC-1-collapse"] = (
        f"Collapsed {len(old_neg)} negative value(s) to one representative: '{representative}'. "
        f"Originals: {old_neg}. All collapsed values produce identical hide/false behavior per user confirmation."
    )
    print(f"[OC-1] Collapsed negatives on '{factor_name}' → {fac['values']}", file=sys.stderr)


def oc1_merge_value(data: dict, factor_name: str, value_to_remove: str, value_to_keep: str):
    fac = find_factor(data["factors"], factor_name)
    if not fac:
        print(f"WARNING: factor '{factor_name}' not found — skipping OC-1 merge", file=sys.stderr)
        return
    fac["values"] = [v for v in fac.get("values", []) if v != value_to_remove]
    fac["negative"] = [v for v in fac.get("negative", []) if v != value_to_remove]
    fac["boundary"] = [v for v in fac.get("boundary", []) if v != value_to_remove]
    fac["positive"] = [v for v in fac.get("positive", []) if v != value_to_remove]
    fac.setdefault("_oc_notes", {})["OC-1-merge"] = (
        f"Merged '{value_to_remove}' into '{value_to_keep}' (higher-risk / same-branch representative). "
        f"Open-phase error noted as sub-scenario in evidence."
    )
    print(f"[OC-1] Removed '{value_to_remove}' from '{factor_name}'; kept representative '{value_to_keep}'", file=sys.stderr)
    print(f"       Remaining values: {fac['values']}", file=sys.stderr)


def oc3_drop_factor(data: dict, dropped_name: str, absorbing_name: str):
    dropped = find_factor(data["factors"], dropped_name)
    if not dropped:
        print(f"WARNING: factor '{dropped_name}' not found — skipping OC-3 drop", file=sys.stderr)
        return
    data["factors"] = [f for f in data["factors"] if f["factor"] != dropped_name]
    absorbing = find_factor(data["factors"], absorbing_name)
    if absorbing:
        absorbing.setdefault("_oc_notes", {})["OC-3"] = (
            f"'{dropped_name}' factor dropped (OC-3). Its values are sub-scenarios of "
            f"'{absorbing_name}'. Verify as data-parameter variants in the relevant rows."
        )
    ensure_dropped(data).append({
        "group": dropped.get("group", ""),
        "factor": dropped_name,
        "reason": f"OC-3: values subsumed into '{absorbing_name}'. Client-side crash/lifecycle variants are sub-scenarios of the failure path already modelled there.",
        "assertions_moved_to": [absorbing_name],
    })
    print(f"[OC-3] Dropped '{dropped_name}' → evidence merged into '{absorbing_name}'", file=sys.stderr)


def oc5_add_constraint(data: dict, constraint: str, target_factor: str | None):
    if target_factor:
        fac = find_factor(data["factors"], target_factor)
        if not fac:
            print(f"WARNING: target factor '{target_factor}' not found — appending constraint as extra_constraints", file=sys.stderr)
            data.setdefault("extra_constraints", []).append(constraint)
            return
        existing = fac.get("pict_constraint", "").strip()
        fac["pict_constraint"] = (existing + " " + constraint).strip() if existing else constraint
        print(f"[OC-5] Appended constraint to '{target_factor}':", file=sys.stderr)
    else:
        data.setdefault("extra_constraints", []).append(constraint)
        print(f"[OC-5] Appended constraint to extra_constraints:", file=sys.stderr)
    print(f"       {constraint}", file=sys.stderr)


def oc8_remove_factor(data: dict, factor_name: str, standalone_rows: list[str]):
    fac = find_factor(data["factors"], factor_name)
    if not fac:
        print(f"WARNING: factor '{factor_name}' not found — skipping OC-8 remove", file=sys.stderr)
        return
    data["factors"] = [f for f in data["factors"] if f["factor"] != factor_name]
    entry = {
        "group": fac.get("group", ""),
        "factor": factor_name,
        "reason": "OC-8: Factor is fully orthogonal to the primary flow; removed from PICT model. Test as standalone rows.",
    }
    if standalone_rows:
        entry["standalone_rows"] = standalone_rows
    ensure_dropped(data).append(entry)
    print(f"[OC-8] Removed '{factor_name}' from PICT model", file=sys.stderr)
    if standalone_rows:
        for row in standalone_rows:
            print(f"       standalone: {row}", file=sys.stderr)


def oc10_drop_factor(data: dict, factor_name: str):
    fac = find_factor(data["factors"], factor_name)
    if not fac:
        print(f"WARNING: factor '{factor_name}' not found — skipping OC-10 drop", file=sys.stderr)
        return
    data["factors"] = [f for f in data["factors"] if f["factor"] != factor_name]
    ensure_dropped(data).append({
        "group": fac.get("group", ""),
        "factor": factor_name,
        "reason": "OC-10: P2 factor dropped — no explicit plan signal; was included speculatively.",
    })
    print(f"[OC-10] Dropped P2 factor '{factor_name}' (speculative, no explicit plan signal)", file=sys.stderr)


def add_seed(data: dict, label: str, kv_pairs: list[str]):
    values = {}
    for kv in kv_pairs:
        if "=" in kv:
            k, _, v = kv.partition("=")
            values[k.strip()] = v.strip()
    ensure_plan_seeds(data).append({
        "source": "plan-seed",
        "label": label,
        "note": "Added via apply_oc_transforms.py --seed flag.",
        "values": values,
    })
    print(f"[SEED] Added seed: {label}", file=sys.stderr)
    if values:
        print(f"       values: {values}", file=sys.stderr)


# ---------------------------------------------------------------------------
# Pipeline runner
# ---------------------------------------------------------------------------

def run_pipeline(factors_path: str, story_id: str):
    build = SCRIPTS_DIR / "build_pict_model.py"
    optimize = SCRIPTS_DIR / "optimize_combinations.py"
    result = subprocess.run(
        f'python3 "{build}" --factors "{factors_path}" 2>/dev/null | '
        f'python3 "{optimize}" --factors "{factors_path}" --story-id "{story_id}" 2>/dev/null',
        shell=True, capture_output=True, text=True,
    )
    if result.returncode != 0:
        print(f"Pipeline error: {result.stderr[:500]}", file=sys.stderr)
        sys.exit(result.returncode)
    try:
        out = json.loads(result.stdout)
        pict_rows = out.get("pict_rows", "?")
        final_rows = out.get("final_rows", "?")
        print(f"\npict_rows={pict_rows}  final_rows={final_rows}")
        return out
    except json.JSONDecodeError:
        print("Pipeline returned non-JSON output:", file=sys.stderr)
        print(result.stdout[:300], file=sys.stderr)
        sys.exit(1)


# ---------------------------------------------------------------------------
# Argument parsing (multi-value args implemented manually)
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Apply Phase 5 OC optimization transforms to an influencing-factors JSON.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--factors",   required=True,  help="Input influencing-factors JSON path")
    parser.add_argument("--out",       required=True,  help="Output (mutated) JSON path")
    parser.add_argument("--story-id",  default=None,   help="JIRA id (required with --run-pipeline)")
    parser.add_argument("--run-pipeline", action="store_true",
                        help="Run build_pict_model + optimize_combinations after writing --out")

    parser.add_argument("--oc1-collapse-negative", nargs=2, metavar=("FACTOR", "REPRESENTATIVE"),
                        action="append", default=[],
                        help="Collapse factor negatives to one representative value")

    parser.add_argument("--oc1-merge-value", nargs=3,
                        metavar=("FACTOR", "VALUE_TO_REMOVE", "VALUE_TO_KEEP"),
                        action="append", default=[],
                        help="Remove a value from a factor and merge into another value")

    parser.add_argument("--oc3-drop-factor", nargs=2,
                        metavar=("DROPPED_FACTOR", "ABSORBING_FACTOR"),
                        action="append", default=[],
                        help="Drop a factor and absorb its evidence into another factor")

    parser.add_argument("--oc5-target", default=None,
                        help="Factor name to attach --oc5-add-constraint to")
    parser.add_argument("--oc5-add-constraint", metavar="CONSTRAINT",
                        action="append", default=[],
                        help="Add a PICT IF/THEN constraint string (may be repeated)")

    parser.add_argument("--oc8-remove-factor", metavar="FACTOR",
                        action="append", default=[],
                        help="Remove a factor from PICT model (OC-8 orthogonal)")
    parser.add_argument("--oc8-standalone-rows", nargs="+",
                        metavar=("FACTOR", "ROW"),
                        action="append", default=[],
                        help="Standalone row descriptions for an OC-8 removed factor "
                             "(first arg = factor name, remaining = row descriptions)")

    parser.add_argument("--oc10-drop-factor", metavar="FACTOR",
                        action="append", default=[],
                        help="Drop a speculative P2 factor (OC-10)")

    parser.add_argument("--seed", nargs="+",
                        metavar=("LABEL", "KV"),
                        action="append", default=[],
                        help="Add a plan-seed (first arg = label, rest = FactorName=value pairs)")

    args = parser.parse_args()

    if args.run_pipeline and not args.story_id:
        parser.error("--story-id is required when --run-pipeline is set")

    data = load(args.factors)

    # Build standalone_rows lookup keyed by factor name
    standalone_lookup: dict[str, list[str]] = {}
    for entry in args.oc8_standalone_rows:
        if entry:
            factor_name = entry[0]
            rows = entry[1:]
            standalone_lookup.setdefault(factor_name, []).extend(rows)

    # Apply transforms in a deterministic order
    for factor_name, representative in args.oc1_collapse_negative:
        oc1_collapse_negative(data, factor_name, representative)

    for factor_name, to_remove, to_keep in args.oc1_merge_value:
        oc1_merge_value(data, factor_name, to_remove, to_keep)

    for dropped, absorbing in args.oc3_drop_factor:
        oc3_drop_factor(data, dropped, absorbing)

    for constraint in args.oc5_add_constraint:
        oc5_add_constraint(data, constraint, args.oc5_target)

    for factor_name in args.oc8_remove_factor:
        rows = standalone_lookup.get(factor_name, [])
        oc8_remove_factor(data, factor_name, rows)

    for factor_name in args.oc10_drop_factor:
        oc10_drop_factor(data, factor_name)

    for seed_args in args.seed:
        if seed_args:
            label = seed_args[0]
            kv_pairs = seed_args[1:]
            add_seed(data, label, kv_pairs)

    save(data, args.out)
    print(f"\nWritten: {args.out}  ({len(data['factors'])} factors remaining)")

    if args.run_pipeline:
        run_pipeline(args.out, args.story_id)


if __name__ == "__main__":
    main()
