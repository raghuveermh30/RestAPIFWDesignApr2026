#!/usr/bin/env python3
"""
Final Gate validator for manh-product-influencing-factor-identifier Stage 1 output.

Usage:
    python3 validate-output.py <path-to-influencing-factors.json>

Exit codes:
    0  ALL PASS
    1  One or more checks failed
"""

import json
import sys


def validate(path: str) -> bool:
    with open(path) as f:
        d = json.load(f)

    checks = {
        "combo_est present and non-zero": d.get("combo_est", 0) > 0,
        "combo_budget present and non-zero": d.get("combo_budget", 0) > 0,
        "practical_total non-zero": d.get("practical_total", {}).get("total", 0) > 0,
        "at least one feature": len(d.get("features", [])) > 0,
        "factors non-empty": len(d.get("factors", [])) > 0,
        "user_finalized is true": d.get("user_finalized") is True,
        "no single-value factor in factors[]": all(
            len(f["values"]) >= 2 for f in d.get("factors", [])
        ),
        "all factors have evidence": all(
            f.get("evidence") for f in d.get("factors", [])
        ),
        "all factors have priority P0/P1/P2": all(
            f.get("priority") in ("P0", "P1", "P2") for f in d.get("factors", [])
        ),
        "all plan_seeds have sparse values (<=3 factors)": all(
            len(s.get("values", {})) <= 3 for s in d.get("plan_seeds", [])
        ),
        "all pict_constraint strings use double-quoted values": all(
            ('"' in f.get("pict_constraint", "") or f.get("pict_constraint", "") == "")
            for f in d.get("factors", [])
        ),
        "every dropped_factor has a reason": all(
            df.get("reason") for df in d.get("dropped_factors", [])
        ),
        "every quality_recovery deferred entry has deferred_reason": all(
            (qr.get("recovery") != "deferred" or bool(qr.get("deferred_reason")))
            for qr in d.get("quality_recovery", [])
        ),
        "combo_budget matches practical_total ceiling (combo_budget >= combo_est)": (
            d.get("combo_budget", 0) >= d.get("combo_est", 0)
        ),
        "practical_total.total == core_matrix + seeds + side_surfaces": (
            d.get("practical_total", {}).get("total", -1)
            == (
                d.get("practical_total", {}).get("core_matrix", 0)
                + d.get("practical_total", {}).get("seeds", 0)
                + d.get("practical_total", {}).get("side_surfaces", 0)
            )
        ),
    }

    all_pass = all(checks.values())
    for label, result in checks.items():
        print(f'  [{"OK  " if result else "FAIL"}] {label}')

    print()
    print("RESULT:", "ALL PASS" if all_pass else "FAILURES DETECTED")
    return all_pass


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(f"Usage: python3 {sys.argv[0]} <path-to-influencing-factors.json>")
        sys.exit(1)
    ok = validate(sys.argv[1])
    sys.exit(0 if ok else 1)
