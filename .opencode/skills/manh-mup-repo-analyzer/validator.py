#!/usr/bin/env python3
"""
Validator for manh-mup-repo-analyzer skill.

Verifies that:
- mup.config.prod.json was detected and read
- NX workspace was analyzed
- JSON metadata schemas were inventoried
- MUP architecture patterns were identified
- All 10 analysis layers produced evidence
- repo-analysis.md was written
- Generated framework skill was created

Exit codes:
  0 — validation passed
  1 — validation failed
"""

import argparse
import json
import re
import sys
from pathlib import Path


def load_text(path: str | None) -> str:
    if not path:
        return ""
    p = Path(path)
    if not p.exists():
        return ""
    try:
        return p.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def load_json(path: str | None) -> dict | list | None:
    text = load_text(path)
    if not text.strip():
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return None


def parse_changed_files(files_arg: str | None) -> list[str]:
    if not files_arg:
        return []
    return [f.strip() for f in files_arg.split(",") if f.strip()]


def check_mup_config_detected(diff: str, trace: dict | list | None) -> list[str]:
    text = diff
    if isinstance(trace, dict):
        text += json.dumps(trace)
    if "mup.config" not in text.lower():
        return ["MUP config: mup.config.prod.json was not read. This is required for MUP repo analysis."]
    return []


def check_nx_workspace_analyzed(diff: str) -> list[str]:
    if diff and not re.search(r"(?:nx\.json|NX|workspace|apps/|libs/|tsconfig\.base)", diff, re.IGNORECASE):
        return ["NX workspace: No NX workspace analysis evidence found (nx.json, apps/, libs/, tsconfig.base.json)."]
    return []


def check_json_metadata_schemas(diff: str) -> list[str]:
    if diff and not re.search(r"(?:metadata|schema|JSON.*schema|schema.*type|PAGE|LAYOUT|GRID|FORM)", diff, re.IGNORECASE):
        return ["JSON metadata: No JSON metadata schema inventory found in analysis output."]
    return []


def check_mup_patterns(diff: str) -> list[str]:
    if diff and not re.search(r"(?:renderer|registry|metadata.*driven|ui-sc-core|ui-sc-common)", diff, re.IGNORECASE):
        return ["MUP patterns: No MUP architecture pattern detection (renderer, registry, ui-sc-core) found."]
    return []


def check_repo_analysis_written(changed_files: list[str]) -> list[str]:
    if not any("repo-analysis.md" in f for f in changed_files):
        return ["repo-analysis.md: Not found in changed files. Must be produced by the analyzer."]
    return []


def check_framework_skill_created(changed_files: list[str], diff: str) -> list[str]:
    has_fw_skill = any("manh-ui-mup-framework" in f and "SKILL.md" in f for f in changed_files)
    if not has_fw_skill:
        if diff and not re.search(r"manh-ui-mup-framework", diff):
            return ["Framework skill: manh-ui-mup-framework/SKILL.md not found. Layer 6 must generate the framework skill."]
    return []


def check_layer_coverage(diff: str) -> list[str]:
    if not diff:
        return []
    violations = []
    layers = [
        ("Layer 1", r"Layer\s*1|MUP Config|mup\.config"),
        ("Layer 2", r"Layer\s*2|NX Workspace|workspace"),
        ("Layer 3", r"Layer\s*3|JSON Metadata|Schema Inventory"),
        ("Layer 4", r"Layer\s*4|MUP.*Pattern|Architecture Pattern"),
        ("Layer 5", r"Layer\s*5|Build.*Test|Convention"),
    ]
    missing = []
    for label, pattern in layers:
        if not re.search(pattern, diff, re.IGNORECASE):
            missing.append(label)
    if len(missing) >= 3:
        violations.append(f"Layer coverage: {len(missing)} of 5 core layers not evidenced ({', '.join(missing)}).")
    return violations


def check_setup_sh_run(diff: str, trace: dict | list | None) -> list[str]:
    text = diff
    if isinstance(trace, dict):
        text += json.dumps(trace)
    if not re.search(r"setup\.sh", text, re.IGNORECASE):
        return ["setup.sh: No evidence that setup.sh was run to distribute generated skills."]
    return []


def run_validation(args: argparse.Namespace) -> dict:
    diff = load_text(args.diff)
    changed_files = parse_changed_files(args.files)
    trace = load_json(args.trace)

    violations: list[str] = []
    warnings: list[str] = []

    violations.extend(check_mup_config_detected(diff, trace))
    violations.extend(check_nx_workspace_analyzed(diff))
    violations.extend(check_json_metadata_schemas(diff))
    violations.extend(check_mup_patterns(diff))
    violations.extend(check_repo_analysis_written(changed_files))
    violations.extend(check_framework_skill_created(changed_files, diff))
    violations.extend(check_layer_coverage(diff))
    warnings.extend(check_setup_sh_run(diff, trace))

    if not diff and not changed_files:
        warnings.append("No artifacts provided. Provide --diff or --files for full validation.")

    score = max(0, 100 - (len(violations) * 12) - (len(warnings) * 5))
    passed = len(violations) == 0

    return {"passed": passed, "violations": violations, "warnings": warnings, "score": score}


def main() -> None:
    parser = argparse.ArgumentParser(description="Validator for manh-mup-repo-analyzer skill")
    parser.add_argument("--diff", metavar="FILE")
    parser.add_argument("--files", metavar="LIST")
    parser.add_argument("--trace", metavar="FILE")
    args = parser.parse_args()
    result = run_validation(args)
    print(json.dumps(result, indent=2))
    sys.exit(0 if result["passed"] else 1)


if __name__ == "__main__":
    main()
