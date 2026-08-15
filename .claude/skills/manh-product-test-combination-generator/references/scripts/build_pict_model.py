#!/usr/bin/env python3
"""
Phase 2+3: Build a PICT model from stage-1 influencing-factors JSON, run pict,
print TSV rows to stdout. No .pict file is left on disk.

Usage:
    python3 build_pict_model.py --factors FACTORS_JSON
                                [--constraints CONSTRAINTS_FILE]
                                [--strength N]

Exit codes: 0 success, 1 error, 2 pict binary not found.
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile


CONFIG_GROUPS = {
    "configurations", "configuration", "business configuration",
    "platform", "form factor", "app network", "network", "checkout mode",
    "gateway", "gateway mode", "register type", "terminal", "feature flag",
    "grant", "grants",
}

CONFIG_FACTOR_KEYWORDS = {
    "feature flag", "grant", "platform", "network", "checkout mode",
    "register type", "gateway", "business config", "store config",
}


def is_config_factor(factor: dict) -> bool:
    group = factor.get("group", "").lower()
    fname = factor.get("factor", "").lower()
    if any(k in group for k in CONFIG_GROUPS):
        return True
    if any(k in fname for k in CONFIG_FACTOR_KEYWORDS):
        return True
    return False


def sanitize_name(name: str) -> str:
    name = name.replace("\n", " ").strip()
    name = name.replace("\u2013", "-").replace("\u2014", "-").replace("\u2019", "'")
    name = name.replace("\u2192", "->").replace("\u00e9", "e").replace("\u00e0", "a")
    name = name.replace("\u2018", "'")
    name = "".join(c if ord(c) < 128 else "_" for c in name)
    return name


def sanitize_value(name: str) -> str:
    return sanitize_name(name).replace('"', "'")


def build_model_text(factors: list, extra_constraints: list[str], strength: int) -> tuple[str, list[str]]:
    lines = []
    p0_params = []
    config_params = []
    total_params = 0

    for f in factors:
        param = sanitize_value(f["factor"])
        values = [sanitize_value(v) for v in f["values"]]
        if not values:
            continue
        lines.append(f'{param}: {", ".join(values)}')
        total_params += 1
        if f.get("priority") == "P0":
            p0_params.append(param)
        if is_config_factor(f):
            config_params.append(param)
            f["_is_config"] = True

    lines.append("")

    # Add P0 sub-model at strength 3 only when the sub-model does NOT cover all parameters
    # (PICT rejects a sub-model that leaves zero "outstanding parameters").
    if len(p0_params) >= 2 and len(p0_params) < total_params:
        sub = ", ".join(p0_params)
        lines.append(f"{{ {sub} }} @ 3")

    for constraint in extra_constraints:
        constraint = constraint.strip()
        if constraint and not constraint.startswith("#"):
            lines.append(constraint)

    return "\n".join(lines), config_params


def run_pict(model_text: str, strength: int) -> str:
    pict_bin = shutil.which("pict")
    if not pict_bin:
        print(
            "ERROR: 'pict' binary not found. Install with: brew install pict",
            file=sys.stderr,
        )
        sys.exit(2)

    with tempfile.NamedTemporaryFile(
        mode="w", suffix=".pict", delete=False, encoding="utf-8"
    ) as tmp:
        tmp.write(model_text)
        tmp_path = tmp.name

    try:
        strengths_to_try = [strength] if strength == 2 else [strength, strength - 1]
        for s in strengths_to_try:
            result = subprocess.run(
                [pict_bin, tmp_path, f"/o:{s}"],
                capture_output=True,
                text=True,
            )
            if result.returncode == 0:
                if s != strength:
                    print(f"# note: fell back from /o:{strength} to /o:{s}", file=sys.stderr)
                return result.stdout
            print(f"# pict /o:{s} failed: {result.stderr.strip()}", file=sys.stderr)
        print(f"ERROR: pict failed at all strengths tried", file=sys.stderr)
        sys.exit(1)
    finally:
        os.unlink(tmp_path)


def main():
    parser = argparse.ArgumentParser(description="Build PICT model and run pict → TSV")
    parser.add_argument("--factors", required=True, help="Path to stage-1 influencing-factors JSON")
    parser.add_argument("--constraints", help="Path to plain-text PICT constraints file (optional)")
    parser.add_argument("--strength", type=int, default=2, help="Default PICT strength (default: 2)")
    args = parser.parse_args()

    with open(args.factors, encoding="utf-8") as f:
        data = json.load(f)

    factors = data.get("factors", [])
    if not factors:
        print("ERROR: No factors found in influencing-factors JSON", file=sys.stderr)
        sys.exit(1)

    if not data.get("user_finalized"):
        print("ERROR: user_finalized is not true — complete stage 1 before running stage 2", file=sys.stderr)
        sys.exit(1)

    extra_constraints = []
    if args.constraints:
        with open(args.constraints, encoding="utf-8") as f:
            extra_constraints = f.readlines()

    # Pick up inline pict_constraint fields from the factors JSON.
    # sanitize_name (not sanitize_value) so double-quotes in PICT syntax are preserved.
    for factor in factors:
        inline = factor.get("pict_constraint", "").strip()
        if inline:
            extra_constraints.append(sanitize_name(inline))

    model_text, config_params = build_model_text(factors, extra_constraints, args.strength)

    print(f"# config_parameters: {json.dumps(config_params)}", file=sys.stderr)
    print(f"# pict model:\n{model_text}", file=sys.stderr)

    tsv = run_pict(model_text, args.strength)
    print(tsv, end="")


if __name__ == "__main__":
    main()
