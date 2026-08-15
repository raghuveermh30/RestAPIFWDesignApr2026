#!/usr/bin/env python3
"""
Phase 4: Read TSV from stdin, apply four optimization passes, print combinations JSON to stdout.

Usage:
    python3 optimize_combinations.py --factors FACTORS_JSON --story-id JIRA_ID
                                      [--seeds-file SEEDS_JSON]
                                      [--prune-file PRUNE_JSON]

Passes (in order):
  1. Seed  — append plan-seed rows, de-duplicate
  2. Prune — remove rows matching impossible-combo rules
  3. Risk-weight — tag high/med/low based on Negative/Boundary presence
  4. Coverage-patch — add minimal rows for any uncovered factor value
"""

import argparse
import json
import sys
from collections import defaultdict


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
    name = name.replace('"', "'").replace("\n", " ").strip()
    name = name.replace("\u2013", "-").replace("\u2014", "-").replace("\u2019", "'")
    name = name.replace("\u2192", "->").replace("\u00e9", "e").replace("\u00e0", "a")
    name = "".join(c if ord(c) < 128 else "_" for c in name)
    return name


def sanitize_values(vals: list) -> list:
    return [sanitize_name(v) for v in vals]


def load_factors(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    factors = data.get("factors", [])
    factor_map = {}
    for fac in factors:
        name = sanitize_name(fac["factor"])
        factor_map[name] = {
            "priority": fac.get("priority", "P2"),
            "is_config": is_config_factor(fac),
            "positive": sanitize_values(fac.get("positive", [])),
            "negative": sanitize_values(fac.get("negative", [])),
            "boundary": sanitize_values(fac.get("boundary", [])),
            "values": sanitize_values(fac.get("values", [])),
        }
    return factor_map


def parse_tsv(tsv_text: str) -> tuple[list[str], list[dict]]:
    lines = [l for l in tsv_text.splitlines() if l.strip()]
    if not lines:
        return [], []
    headers = [h.strip() for h in lines[0].split("\t")]
    rows = []
    for line in lines[1:]:
        parts = [p.strip() for p in line.split("\t")]
        if len(parts) != len(headers):
            continue
        rows.append(dict(zip(headers, parts)))
    return headers, rows


def row_key(row: dict) -> str:
    return json.dumps(row, sort_keys=True)


_behavioral_scenarios: list[dict] = []  # module-level collector for de-duped labeled seeds


def pass1_seed(rows: list[dict], headers: list[str], seeds: list[dict], factor_map: dict) -> list[dict]:
    existing_keys = {row_key(r) for r in rows}
    for seed in seeds:
        seed_values = seed.get("values", {})
        full_row = {}
        for h in headers:
            if h in seed_values:
                full_row[h] = seed_values[h]
            else:
                fac = factor_map.get(h, {})
                positive = fac.get("positive", [])
                all_vals = fac.get("values", [])
                full_row[h] = positive[0] if positive else (all_vals[0] if all_vals else "")
        full_row["_source"] = "plan-seed"
        if "label" in seed:
            full_row["_label"] = seed["label"]
        k = row_key({k: v for k, v in full_row.items() if not k.startswith("_")})
        if k not in existing_keys:
            rows.append(full_row)
            existing_keys.add(k)
        else:
            # Row already exists — if this seed has a distinct label, record it as a behavioral scenario
            if "label" in seed:
                note = seed.get("note", seed.get("_note", ""))
                existing_labels = {bs["label"] for bs in _behavioral_scenarios}
                if seed["label"] not in existing_labels:
                    _behavioral_scenarios.append({
                        "id": f"BS{len(_behavioral_scenarios)+1:03d}",
                        "label": seed["label"],
                        "note": note,
                        "values": {k: v for k, v in full_row.items() if not k.startswith("_")},
                    })
    return rows


def pass2_prune(rows: list[dict], prune_rules: list[dict]) -> tuple[list[dict], list[str]]:
    kept = []
    pruned_reasons = []
    for row in rows:
        prune = False
        for rule in prune_rules:
            match = rule.get("match", {})
            if all(row.get(k) == v for k, v in match.items()):
                prune = True
                reason = rule.get("reason", str(match))
                if reason not in pruned_reasons:
                    pruned_reasons.append(reason)
                break
        if not prune:
            kept.append(row)
    return kept, pruned_reasons


def pass3_risk(rows: list[dict], factor_map: dict) -> list[dict]:
    for row in rows:
        risk = "low"
        for param, value in row.items():
            if param.startswith("_"):
                continue
            fac = factor_map.get(param, {})
            priority = fac.get("priority", "P2")
            negatives = fac.get("negative", [])
            boundaries = fac.get("boundary", [])
            if value in negatives or value in boundaries:
                if priority == "P0":
                    risk = "high"
                elif priority == "P1" and risk != "high":
                    risk = "high"
                elif priority == "P2" and risk == "low":
                    risk = "med"
        row["_risk"] = risk
    return rows


def pass4_coverage(rows: list[dict], headers: list[str], factor_map: dict) -> list[dict]:
    covered = defaultdict(set)
    for row in rows:
        for param in headers:
            if param in row:
                covered[param].add(row[param])

    for param in headers:
        fac = factor_map.get(param, {})
        all_vals = fac.get("values", [])
        for val in all_vals:
            if val not in covered[param]:
                new_row = {}
                for h in headers:
                    f = factor_map.get(h, {})
                    pos = f.get("positive", [])
                    vals = f.get("values", [])
                    new_row[h] = pos[0] if pos else (vals[0] if vals else "")
                new_row[param] = val
                new_row["_source"] = "coverage-patch"
                new_row["_risk"] = "med"
                rows.append(new_row)
                covered[param].add(val)
                print(f"# coverage-patch: added row for {param}={val}", file=sys.stderr)

    return rows


def build_output(rows: list[dict], headers: list[str], factor_map: dict,
                 story_id: str, pict_rows: int, pruned_reasons: list[str],
                 guard_invariant: list[dict]) -> dict:
    config_params = [h for h in headers if factor_map.get(h, {}).get("is_config")]
    combinations = []
    for i, row in enumerate(rows, 1):
        values = {k: v for k, v in row.items() if not k.startswith("_")}
        entry = {
            "id": f"C{i:03d}",
            "values": values,
            "risk": row.get("_risk", "low"),
            "source": row.get("_source", "pict"),
        }
        if "_label" in row:
            entry["label"] = row["_label"]
        combinations.append(entry)

    return {
        "story_id": story_id,
        "strength": 2,
        "parameters": headers,
        "config_parameters": config_params,
        "guard_invariant": guard_invariant,
        "pict_rows": pict_rows,
        "final_rows": len(rows),
        "pruned_rows": pict_rows - len(rows) + len([r for r in rows if r.get("_source") in ("plan-seed", "coverage-patch")]),
        "pruned_reasons": pruned_reasons,
        "combinations": combinations,
        "behavioral_scenarios": _behavioral_scenarios,
    }


def main():
    parser = argparse.ArgumentParser(description="Optimize PICT TSV → combinations JSON")
    parser.add_argument("--factors", required=True)
    parser.add_argument("--story-id", required=True)
    parser.add_argument("--seeds-file", help="JSON array of seed rows")
    parser.add_argument("--prune-file", help="JSON array of prune rules")
    args = parser.parse_args()

    factor_map = load_factors(args.factors)

    with open(args.factors, encoding="utf-8") as f:
        factors_data = json.load(f)
    guard_invariant = factors_data.get("guard_invariant", [])

    tsv_text = sys.stdin.read()
    headers, rows = parse_tsv(tsv_text)
    if not headers:
        print("ERROR: No TSV input received on stdin", file=sys.stderr)
        sys.exit(1)

    pict_rows = len(rows)
    print(f"# pict_rows: {pict_rows}", file=sys.stderr)

    # Load seeds: prefer plan_seeds[] from factors JSON (carries labels), merge with --seeds-file
    seeds = []
    plan_seeds_raw = factors_data.get("plan_seeds", [])
    for ps in plan_seeds_raw:
        entry = {"source": ps.get("source", "plan-seed"),
                 "values": {k: v for k, v in ps.get("values", {}).items() if not k.startswith("_")}}
        if "label" in ps:
            entry["label"] = ps["label"]
        seeds.append(entry)
    if args.seeds_file:
        with open(args.seeds_file, encoding="utf-8") as f:
            extra = json.load(f)
        # Only add seeds from file that aren't already covered by plan_seeds
        seeds.extend(extra)

    prune_rules = []
    if args.prune_file:
        with open(args.prune_file, encoding="utf-8") as f:
            prune_rules = json.load(f)

    rows = pass1_seed(rows, headers, seeds, factor_map)
    rows, pruned_reasons = pass2_prune(rows, prune_rules)
    rows = pass3_risk(rows, factor_map)
    rows = pass4_coverage(rows, headers, factor_map)

    output = build_output(rows, headers, factor_map, args.story_id,
                          pict_rows, pruned_reasons, guard_invariant)
    print(json.dumps(output, indent=2))


if __name__ == "__main__":
    main()
