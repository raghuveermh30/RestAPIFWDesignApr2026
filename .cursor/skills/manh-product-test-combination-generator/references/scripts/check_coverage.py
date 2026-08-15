#!/usr/bin/env python3
"""
check_coverage.py — Verify value coverage and print row-count summary for a
combinations JSON produced by optimize_combinations.py.

Reads combinations JSON from stdin (piped from optimize_combinations.py or
from a saved file) and the stage-1 influencing-factors JSON via --factors.
Prints pict_rows, final_rows, a per-value coverage table, and a by-source
breakdown. Exits non-zero if any factor value has zero coverage.

Usage (pipe from optimize_combinations.py):
    python3 references/scripts/build_pict_model.py --factors FACTORS \\
      | python3 references/scripts/optimize_combinations.py --factors FACTORS --story-id JIRA_ID \\
      | python3 references/scripts/check_coverage.py --factors FACTORS

Usage (from saved combinations JSON):
    python3 references/scripts/check_coverage.py \\
        --factors FACTORS \\
        --combinations COMBINATIONS_JSON

Options:
    --factors      Path to stage-1 *-influencing-factors.json  (required)
    --combinations Path to a saved combinations JSON file       (optional;
                   if omitted, reads from stdin)

Exit codes:
    0   All factor values covered
    1   One or more factor values have zero coverage (prints MISSING lines)
"""

import argparse
import json
import sys
from collections import Counter


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Check value coverage of a combinations JSON against the factors definition."
    )
    parser.add_argument("--factors", required=True, help="Path to stage-1 influencing-factors JSON")
    parser.add_argument(
        "--combinations",
        default=None,
        help="Path to combinations JSON file (default: read from stdin)",
    )
    args = parser.parse_args()

    if args.combinations:
        with open(args.combinations) as fh:
            data = json.load(fh)
    else:
        data = json.load(sys.stdin)

    with open(args.factors) as fh:
        factors_data = json.load(fh)

    pict_rows = data.get("pict_rows", "n/a")
    final_rows = data.get("final_rows", len(data.get("combinations", [])))
    combos = data.get("combinations", [])

    print(f"pict_rows:  {pict_rows}")
    print(f"final_rows: {final_rows}")
    print()

    missing = []
    print("--- value coverage check ---")
    for factor in factors_data.get("factors", []):
        fname = factor["factor"]
        for val in factor.get("values", []):
            count = sum(1 for c in combos if c.get("values", {}).get(fname) == val)
            status = "OK" if count > 0 else "MISSING"
            if status == "MISSING":
                missing.append((fname, val))
            print(f"  [{status}] {fname} = {val!r}  ({count} rows)")

    print()
    by_source = Counter(c.get("source", "unknown") for c in combos)
    print("by source:", dict(by_source))

    if missing:
        print()
        print("ERROR: the following values have zero coverage:")
        for fname, val in missing:
            print(f"  MISSING  {fname} = {val!r}")
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
