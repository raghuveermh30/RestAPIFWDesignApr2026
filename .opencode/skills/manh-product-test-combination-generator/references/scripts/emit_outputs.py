#!/usr/bin/env python3
"""
Phase 5: Read combinations JSON from stdin, run final gate checks,
write <story-id>-test-combinations.md and <story-id>-test-combinations.json
into --out-dir.

Usage:
    python3 emit_outputs.py --factors FACTORS_JSON --story-id JIRA_ID --out-dir OUT_DIR
"""

import argparse
import json
import os
import re
import sys


def sanitize_name(name: str) -> str:
    name = name.replace('"', "'").replace("\n", " ").strip()
    name = name.replace("\u2013", "-").replace("\u2014", "-").replace("\u2019", "'")
    name = name.replace("\u2192", "->").replace("\u00e9", "e").replace("\u00e0", "a")
    name = "".join(c if ord(c) < 128 else "_" for c in name)
    return name


def load_factors(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        raw = json.load(f)
    raw["factors"] = [
        {**f, "factor": sanitize_name(f["factor"]),
         "values": [sanitize_name(v) for v in f.get("values", [])]}
        for f in raw.get("factors", [])
    ]
    return raw


def final_gate(data: dict, factor_map: dict) -> list[str]:
    failures = []
    combos = data.get("combinations", [])

    if not combos:
        failures.append("combinations[] is empty")
        return failures

    if any("id" not in c for c in combos):
        failures.append("One or more combinations missing 'id' field")

    covered = {p: set() for p in data.get("parameters", [])}
    for combo in combos:
        for param, val in combo.get("values", {}).items():
            if param in covered:
                covered[param].add(val)

    excluded = {f.get("factor"): f.get("excluded_by_constraint") for f in factor_map.get("factors", [])}

    for fac in factor_map.get("factors", []):
        param = fac["factor"]
        all_vals = fac.get("values", [])
        if param not in covered:
            continue
        for val in all_vals:
            if val not in covered[param]:
                if not excluded.get(param):
                    failures.append(f"Uncovered value: {param} = {val!r}")

    return failures


def build_md(data: dict) -> str:
    story_id = data["story_id"]
    lines = [f"# {story_id} Test Combinations — Stage 2", ""]

    lines += ["## Optimization Summary", ""]
    lines += [
        "| Metric | Value |",
        "|--------|-------|",
        f"| PICT model parameters | {len(data.get('parameters', []))} |",
        f"| PICT strength | {data.get('strength', 2)} |",
        f"| Raw PICT rows | {data.get('pict_rows', '?')} |",
        f"| Pruned rows | {data.get('pruned_rows', 0)} |",
        f"| Final combinations | {data.get('final_rows', len(data.get('combinations', [])))} |",
    ]

    from collections import Counter
    risks = Counter(c.get("risk", "low") for c in data.get("combinations", []))
    sources = Counter(c.get("source", "pict") for c in data.get("combinations", []))
    lines += [
        f"| High-risk rows | {risks.get('high', 0)} |",
        f"| Med-risk rows | {risks.get('med', 0)} |",
        f"| Low-risk rows | {risks.get('low', 0)} |",
        f"| Plan-seeded rows | {sources.get('plan-seed', 0)} |",
        f"| Coverage-patch rows | {sources.get('coverage-patch', 0)} |",
        f"| PICT-generated rows | {sources.get('pict', 0)} |",
    ]
    lines.append("")

    guard_invariant = data.get("guard_invariant", [])
    if guard_invariant:
        lines += ["### Guard-invariant Collapses", ""]
        lines += ["| Factor | Collapsed to | Reason |", "|--------|-------------|--------|"]
        for g in guard_invariant:
            factor = g.get("factor", "")
            collapsed = g.get("collapsed_to", "")
            reason = g.get("reason", g.get("evidence", ""))
            lines.append(f"| {factor} | {collapsed} | {reason} |")
        lines.append("")

    pruned_reasons = data.get("pruned_reasons", [])
    if pruned_reasons:
        lines += ["### Pruned Rows", ""]
        for i, reason in enumerate(pruned_reasons, 1):
            lines.append(f"{i}. {reason}")
        lines.append("")

    # Behavioral scenarios from plan_seeds that are multi-step sequences,
    # not expressible as distinct PICT parameter rows (same inputs, different intent)
    behavioral_scenarios = data.get("behavioral_scenarios", [])
    if behavioral_scenarios:
        lines += ["---", "", "## Behavioral Test Scenarios", ""]
        lines.append("These are multi-step behavioral sequences that require explicit test case design in Stage 3.")
        lines.append("They share factor values with existing PICT rows but test distinct behavioral intent.")
        lines.append("")
        for bs in behavioral_scenarios:
            lines.append(f"**{bs['id']}** — {bs['label']}")
            lines.append(f"_{bs['note']}_")
            lines.append("")

    lines += ["---", "", "## Combinations", ""]

    parameters = data.get("parameters", [])
    if parameters:
        header_row = "| ID | " + " | ".join(parameters) + " | Risk | Source |"
        separator = "|-----|" + "|".join(["---"] * len(parameters)) + "|------|--------|"
        lines.append(header_row)
        lines.append(separator)

        for combo in data.get("combinations", []):
            cid = combo.get("id", "")
            risk = combo.get("risk", "")
            source = combo.get("source", "")
            vals = combo.get("values", {})
            row_cells = [str(vals.get(p, "")) for p in parameters]
            row = f"| {cid} | " + " | ".join(row_cells) + f" | {risk} | {source} |"
            lines.append(row)

    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Emit stage-2 .md and .json output files")
    parser.add_argument("--factors", required=True, help="Path to stage-1 influencing-factors JSON")
    parser.add_argument("--story-id", required=True, help="JIRA ticket id (e.g. POS-123456)")
    parser.add_argument("--out-dir", required=True, help="Output directory")
    args = parser.parse_args()

    factor_map = load_factors(args.factors)

    raw = sys.stdin.read()
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as e:
        print(f"ERROR: Invalid JSON on stdin: {e}", file=sys.stderr)
        sys.exit(1)

    failures = final_gate(data, factor_map)
    if failures:
        print("FINAL GATE FAILED:", file=sys.stderr)
        for f in failures:
            print(f"  ✗ {f}", file=sys.stderr)
        sys.exit(1)

    os.makedirs(args.out_dir, exist_ok=True)

    json_path = os.path.join(args.out_dir, f"{args.story_id}-test-combinations.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    md_content = build_md(data)

    if re.search(r"```json", md_content):
        print("ERROR: MD output contains a JSON code block — forbidden by output-format.md", file=sys.stderr)
        sys.exit(1)

    md_path = os.path.join(args.out_dir, f"{args.story_id}-test-combinations.md")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write(md_content)

    print(f"✅ Written: {json_path}")
    print(f"✅ Written: {md_path}")
    print(f"   {len(data.get('combinations', []))} combinations, final gate passed.")


if __name__ == "__main__":
    main()
