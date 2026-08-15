#!/usr/bin/env python3
"""
Stage 2 — Full pipeline runner: build PICT model → optimize combinations → emit outputs.

Replaces all inline `python3 << 'PYEOF'` one-offs used during optimization sessions.
Handles constraint merging, sub-model control, and the full optimization loop in one pass.

Usage:
    python3 run_pipeline.py \\
        --factors   <product-repo>/products/<product>/test/POS-180453/POS-180453_influencing-factors.json \\
        --story-id  POS-180453 \\
        --out-dir   <product-repo>/products/<product>/test/POS-180453 \\
        [--constraints  path/to/extra-constraints.txt] \\
        [--seeds        path/to/seeds.json] \\
        [--prune        path/to/prune-rules.json] \\
        [--no-submodel] \\
        [--strength N]  (default: 2)

Exit codes: 0 success, 1 error, 2 pict binary not found.

Constraint file format (plain text, one PICT IF/THEN per line):
    IF [Factor A] = "value" THEN [Factor B] = "value";
    IF [Factor A] = "value" THEN [Factor B] <> "value";
    # comment lines are ignored

Seeds file format (JSON array):
    [{"source":"plan-seed","label":"<name>","note":"<why>","values":{"Factor":"value",...}}]

Prune rules format (JSON array):
    [{"reason":"<why>","match":{"Factor":"value","Factor2":"value2"}}]
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
from collections import Counter, defaultdict

# ---------------------------------------------------------------------------
# Shared helpers (mirrors sanitize_name in the three pipeline scripts)
# ---------------------------------------------------------------------------

_UNICODE_MAP = [
    ("\u2013", "-"), ("\u2014", "-"), ("\u2019", "'"),
    ("\u2192", "->"), ("\u00e9", "e"), ("\u00e0", "a"),
]


def sanitize(name: str) -> str:
    name = name.replace('"', "'").replace("\n", " ").strip()
    for old, new in _UNICODE_MAP:
        name = name.replace(old, new)
    return "".join(c if ord(c) < 128 else "_" for c in name)


def sanitize_constraint(raw: str) -> str:
    """Normalize unicode in a constraint string but preserve double-quotes (PICT syntax)."""
    for old, new in _UNICODE_MAP:
        raw = raw.replace(old, new)
    return raw.strip()


CONFIG_GROUPS = {
    "configurations", "configuration", "business configuration",
    "platform", "form factor", "app network", "network", "checkout mode",
    "gateway", "gateway mode", "register type", "terminal", "feature flag",
    "grant", "grants",
}
CONFIG_KEYWORDS = {
    "feature flag", "grant", "platform", "network", "checkout mode",
    "register type", "gateway", "business config", "store config",
}


def is_config(factor: dict) -> bool:
    g = factor.get("group", "").lower()
    n = factor.get("factor", "").lower()
    return any(k in g for k in CONFIG_GROUPS) or any(k in n for k in CONFIG_KEYWORDS)


# ---------------------------------------------------------------------------
# Phase 2+3 — Build PICT model and run pict
# ---------------------------------------------------------------------------

def build_and_run_pict(factors: list, extra_constraints: list[str],
                       strength: int, no_submodel: bool) -> str:
    """
    Build the PICT model text, run pict, return raw TSV string.
    Raises SystemExit(2) if pict binary not found.
    """
    lines = []
    p0_params = []

    for fac in factors:
        name = sanitize(fac["factor"])
        vals = [sanitize(v) for v in fac["values"]]
        if not vals:
            continue
        lines.append(f'{name}: {", ".join(vals)}')
        if fac.get("priority") == "P0":
            p0_params.append(name)

    lines.append("")

    if not no_submodel and len(p0_params) >= 2:
        lines.append(f'{{ {", ".join(p0_params)} }} @ 3')
        print(f"# P0 sub-model @ 3: {p0_params}", file=sys.stderr)
    else:
        print("# P0 sub-model: disabled (--no-submodel or < 2 P0 params)", file=sys.stderr)

    # Inline pict_constraint fields from factors JSON
    inline_constraints = []
    for fac in factors:
        raw = fac.get("pict_constraint", "").strip()
        if raw:
            inline_constraints.append(sanitize_constraint(raw))

    all_constraints = inline_constraints + [sanitize_constraint(c) for c in extra_constraints]
    for c in all_constraints:
        if c and not c.startswith("#"):
            lines.append(c)

    model_text = "\n".join(lines)
    print(f"# PICT model:\n{model_text}\n", file=sys.stderr)

    pict_bin = shutil.which("pict")
    if not pict_bin:
        print("ERROR: 'pict' binary not found. Install with: brew install pict", file=sys.stderr)
        sys.exit(2)

    with tempfile.NamedTemporaryFile(mode="w", suffix=".pict", delete=False, encoding="utf-8") as tmp:
        tmp.write(model_text)
        tmp_path = tmp.name

    try:
        result = subprocess.run(
            [pict_bin, tmp_path, f"/o:{strength}"],
            capture_output=True, text=True,
        )
        if result.returncode != 0:
            print(f"ERROR: pict failed:\n{result.stderr}", file=sys.stderr)
            sys.exit(1)
        return result.stdout
    finally:
        os.unlink(tmp_path)


# ---------------------------------------------------------------------------
# Phase 4 — Optimize combinations
# ---------------------------------------------------------------------------

def row_key(row: dict) -> str:
    return json.dumps({k: v for k, v in row.items() if not k.startswith("_")}, sort_keys=True)


def optimize(tsv: str, factors: list, story_id: str,
             seeds: list[dict], prune_rules: list[dict]) -> dict:
    # Parse TSV
    lines = [l for l in tsv.splitlines() if l.strip()]
    if not lines:
        print("ERROR: no TSV rows from pict", file=sys.stderr)
        sys.exit(1)
    headers = [h.strip() for h in lines[0].split("\t")]
    rows = []
    for line in lines[1:]:
        parts = [p.strip() for p in line.split("\t")]
        if len(parts) == len(headers):
            rows.append(dict(zip(headers, parts)))

    pict_rows = len(rows)
    print(f"# pict_rows: {pict_rows}", file=sys.stderr)

    # Build factor map
    fmap = {}
    for fac in factors:
        name = sanitize(fac["factor"])
        fmap[name] = {
            "priority": fac.get("priority", "P2"),
            "is_config": is_config(fac),
            "positive": [sanitize(v) for v in fac.get("positive", [])],
            "negative": [sanitize(v) for v in fac.get("negative", [])],
            "boundary": [sanitize(v) for v in fac.get("boundary", [])],
            "values": [sanitize(v) for v in fac.get("values", [])],
        }

    # Pass 1 — seed
    existing = {row_key(r) for r in rows}
    behavioral = []
    for seed in seeds:
        sv = seed.get("values", {})
        full = {}
        for h in headers:
            full[h] = sv.get(h, (fmap.get(h, {}).get("positive") or fmap.get(h, {}).get("values") or [""])[0])
        full["_source"] = "plan-seed"
        if "label" in seed:
            full["_label"] = seed["label"]
        k = row_key(full)
        if k not in existing:
            rows.append(full)
            existing.add(k)
        elif "label" in seed:
            behavioral.append({"id": f"BS{len(behavioral)+1:03d}",
                                "label": seed["label"],
                                "note": seed.get("note", ""),
                                "values": {kk: vv for kk, vv in full.items() if not kk.startswith("_")}})

    # Pass 2 — prune
    pruned_reasons = []
    kept = []
    for row in rows:
        prune = False
        for rule in prune_rules:
            if all(row.get(k) == v for k, v in rule.get("match", {}).items()):
                r = rule.get("reason", str(rule["match"]))
                if r not in pruned_reasons:
                    pruned_reasons.append(r)
                prune = True
                break
        if not prune:
            kept.append(row)
    rows = kept

    # Pass 3 — risk weight
    for row in rows:
        risk = "low"
        for param, val in row.items():
            if param.startswith("_"):
                continue
            f = fmap.get(param, {})
            priority = f.get("priority", "P2")
            if val in f.get("negative", []) or val in f.get("boundary", []):
                if priority in ("P0", "P1"):
                    risk = "high"
                elif priority == "P2" and risk == "low":
                    risk = "med"
        row["_risk"] = risk

    # Pass 4 — coverage patch
    covered = defaultdict(set)
    for row in rows:
        for h in headers:
            if h in row:
                covered[h].add(row[h])
    for h in headers:
        for val in fmap.get(h, {}).get("values", []):
            if val not in covered[h]:
                new_row = {}
                for hh in headers:
                    f = fmap.get(hh, {})
                    pos = f.get("positive", [])
                    vals = f.get("values", [])
                    new_row[hh] = pos[0] if pos else (vals[0] if vals else "")
                new_row[h] = val
                new_row["_source"] = "coverage-patch"
                new_row["_risk"] = "med"
                rows.append(new_row)
                covered[h].add(val)
                print(f"# coverage-patch: {h} = {val}", file=sys.stderr)

    # Build output
    config_params = [h for h in headers if fmap.get(h, {}).get("is_config")]
    combinations = []
    for i, row in enumerate(rows, 1):
        entry = {
            "id": f"C{i:03d}",
            "values": {k: v for k, v in row.items() if not k.startswith("_")},
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
        "guard_invariant": [],
        "pict_rows": pict_rows,
        "final_rows": len(rows),
        "pruned_rows": pict_rows - len(rows) + len([r for r in rows if r.get("_source") in ("plan-seed", "coverage-patch")]),
        "pruned_reasons": pruned_reasons,
        "combinations": combinations,
        "behavioral_scenarios": behavioral,
    }


# ---------------------------------------------------------------------------
# Phase 6 — Emit
# ---------------------------------------------------------------------------

def emit(data: dict, factors_path: str, story_id: str, out_dir: str):
    os.makedirs(out_dir, exist_ok=True)

    # Final gate: all factor values covered
    with open(factors_path, encoding="utf-8") as f:
        fdata = json.load(f)
    covered = defaultdict(set)
    for c in data["combinations"]:
        for p, v in c["values"].items():
            covered[p].add(v)
    failures = []
    for fac in fdata.get("factors", []):
        name = sanitize(fac["factor"])
        for val in fac.get("values", []):
            sval = sanitize(val)
            if name in data["parameters"] and sval not in covered.get(name, set()):
                failures.append(f"Uncovered: {name} = {val!r}")
    if failures:
        print("FINAL GATE FAILED:", file=sys.stderr)
        for f in failures:
            print(f"  ✗ {f}", file=sys.stderr)
        sys.exit(1)

    # Write JSON
    json_path = os.path.join(out_dir, f"{story_id}-test-combinations.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    # Write MD
    lines = [f"# {story_id} Test Combinations — Stage 2", ""]
    lines += ["## Optimization Summary", ""]
    risks = Counter(c.get("risk", "low") for c in data["combinations"])
    sources = Counter(c.get("source", "pict") for c in data["combinations"])
    lines += [
        "| Metric | Value |", "|--------|-------|",
        f"| PICT model parameters | {len(data.get('parameters', []))} |",
        f"| PICT strength | {data.get('strength', 2)} |",
        f"| Raw PICT rows | {data.get('pict_rows', '?')} |",
        f"| Pruned rows | {data.get('pruned_rows', 0)} |",
        f"| Final combinations | {data.get('final_rows', len(data.get('combinations', [])))} |",
        f"| High-risk rows | {risks.get('high', 0)} |",
        f"| Med-risk rows | {risks.get('med', 0)} |",
        f"| Low-risk rows | {risks.get('low', 0)} |",
        f"| Plan-seeded rows | {sources.get('plan-seed', 0)} |",
        f"| Coverage-patch rows | {sources.get('coverage-patch', 0)} |",
        f"| PICT-generated rows | {sources.get('pict', 0)} |",
        "",
    ]
    for r in data.get("pruned_reasons", []):
        lines.append(f"Pruned: {r}")
    if data.get("pruned_reasons"):
        lines.append("")

    bs = data.get("behavioral_scenarios", [])
    if bs:
        lines += ["---", "", "## Behavioral Test Scenarios", ""]
        for b in bs:
            lines.append(f"**{b['id']}** — {b['label']}")
            lines.append(f"_{b['note']}_")
            lines.append("")

    lines += ["---", "", "## Combinations", ""]
    params = data.get("parameters", [])
    if params:
        lines.append("| ID | " + " | ".join(params) + " | Risk | Source |")
        lines.append("|-----|" + "|".join(["---"] * len(params)) + "|------|--------|")
        for c in data["combinations"]:
            vals = [str(c["values"].get(p, "")) for p in params]
            lines.append(f"| {c['id']} | " + " | ".join(vals) + f" | {c['risk']} | {c['source']} |")
    lines.append("")
    md_content = "\n".join(lines)

    md_path = os.path.join(out_dir, f"{story_id}-test-combinations.md")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write(md_content)

    print(f"✅ Written: {json_path}")
    print(f"✅ Written: {md_path}")
    print(f"   {len(data['combinations'])} combinations, final gate passed.")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Stage 2 full pipeline: PICT model → optimize → emit",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--factors",      required=True,  help="Path to stage-1 *-influencing-factors.json")
    parser.add_argument("--story-id",     required=True,  help="JIRA ticket id, e.g. POS-180453")
    parser.add_argument("--out-dir",      required=True,  help="Output directory for .md and .json")
    parser.add_argument("--constraints",  default=None,   help="Extra PICT IF/THEN constraints file (optional)")
    parser.add_argument("--seeds",        default=None,   help="Additional seeds JSON file (optional)")
    parser.add_argument("--prune",        default=None,   help="Prune rules JSON file (optional)")
    parser.add_argument("--no-submodel",  action="store_true",
                        help="Disable automatic P0 sub-model @ strength 3 (use strength 2 pairwise only)")
    parser.add_argument("--strength",     type=int, default=2,
                        help="Base PICT interaction strength (default: 2)")
    args = parser.parse_args()

    # Load factors
    with open(args.factors, encoding="utf-8") as f:
        data = json.load(f)

    if not data.get("user_finalized"):
        print("ERROR: user_finalized is not true — complete stage 1 first", file=sys.stderr)
        sys.exit(1)

    factors = data.get("factors", [])
    if not factors:
        print("ERROR: no factors found in influencing-factors JSON", file=sys.stderr)
        sys.exit(1)

    # Load extra constraints
    extra_constraints = []
    if args.constraints:
        with open(args.constraints, encoding="utf-8") as f:
            extra_constraints = [l.rstrip("\n") for l in f if l.strip() and not l.strip().startswith("#")]

    # Load seeds: start from plan_seeds[] in factors JSON, then merge --seeds file
    seeds = []
    for ps in data.get("plan_seeds", []):
        entry = {"source": ps.get("source", "plan-seed"),
                 "values": {k: v for k, v in ps.get("values", {}).items() if not k.startswith("_")}}
        if "label" in ps:
            entry["label"] = ps["label"]
        if "note" in ps:
            entry["note"] = ps["note"]
        seeds.append(entry)
    if args.seeds:
        with open(args.seeds, encoding="utf-8") as f:
            seeds.extend(json.load(f))

    # Load prune rules
    prune_rules = []
    if args.prune:
        with open(args.prune, encoding="utf-8") as f:
            prune_rules = json.load(f)

    # Run pipeline
    tsv = build_and_run_pict(factors, extra_constraints, args.strength, args.no_submodel)
    result = optimize(tsv, factors, args.story_id, seeds, prune_rules)
    emit(result, args.factors, args.story_id, args.out_dir)


if __name__ == "__main__":
    main()
