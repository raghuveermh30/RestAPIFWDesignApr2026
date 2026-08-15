#!/usr/bin/env python3
"""
check_constraint_values.py — Phase 2 pre-flight guard.

Verifies that every double-quoted value token inside a factor's
pict_constraint field exactly matches a string in factors[].values[]
(or a factor name). PICT silently ignores constraints whose value tokens
do not match exactly, allowing impossible rows to pass through.

Usage:
    python3 references/scripts/check_constraint_values.py --factors FACTORS_JSON

Options:
    --factors   Path to stage-1 *_influencing-factors.json  (required)

Exit codes:
    0   All constraint value tokens match factors[].values[]
    1   One or more mismatches detected (prints details and nearest match)
"""

import argparse
import json
import re
import sys


def nearest(token: str, candidates: set) -> str:
    token_l = token.lower()
    for c in sorted(candidates):
        if token_l in c.lower() or c.lower().startswith(token_l[:8]):
            return c
    return "(no close match found)"


def main():
    parser = argparse.ArgumentParser(
        description="Verify pict_constraint value tokens match factors[].values[]"
    )
    parser.add_argument(
        "--factors", required=True,
        help="Path to stage-1 *_influencing-factors.json"
    )
    args = parser.parse_args()

    with open(args.factors, encoding="utf-8") as f:
        data = json.load(f)

    factors = data.get("factors", [])
    value_set = {v for fac in factors for v in fac.get("values", [])}
    factor_names = {fac["factor"] for fac in factors}
    allowed = value_set | factor_names

    errors = []
    for fac in factors:
        constraint = fac.get("pict_constraint", "").strip()
        if not constraint:
            continue
        for token in re.findall(r'"([^"]+)"', constraint):
            if token not in allowed:
                suggestion = nearest(token, value_set)
                errors.append(
                    f'  MISMATCH in [{fac["factor"]}]:\n'
                    f'    constraint token : "{token}"\n'
                    f'    nearest value    : "{suggestion}"\n'
                    f'    fix: update pict_constraint to use the exact value string'
                )

    if errors:
        print("CONSTRAINT VALUE MISMATCHES DETECTED — fix before running build_pict_model.py:\n")
        for e in errors:
            print(e)
        sys.exit(1)

    print(f"OK — all constraint tokens match factors[].values[] ({len(factors)} factors checked)")


if __name__ == "__main__":
    main()
