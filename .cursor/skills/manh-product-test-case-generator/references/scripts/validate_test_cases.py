#!/usr/bin/env python3
"""
Stage 4 final-gate validator.

Checks that the two output files written by manh-product-test-case-generator
satisfy every constraint in references/output-format.md before stage 5 runs.

Usage:
    python3 validate_test_cases.py --story-id STORY_ID --out-dir OUT_DIR
                                   [--expected-count N]

Arguments:
    --story-id        JIRA ticket id used as the file name prefix (e.g. OM-122824).
    --out-dir         Directory containing <story_id>_test-cases.md and
                      <story_id>_test-cases.json (absolute or relative path).
    --expected-count  Exact number of test cases expected. When omitted the
                      check is skipped (useful when scope = single scenario).

Exit codes:
    0  All gate checks passed.
    1  One or more gate checks failed (failures printed to stdout).
    2  A required file could not be read.
"""

import argparse
import json
import os
import re
import sys


VALID_POLARITIES = {"Positive", "Negative", "Boundary"}
VALID_RISKS = {"high", "med", "low"}


def load_json(path: str) -> dict:
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"ERROR: File not found: {path}", file=sys.stderr)
        sys.exit(2)
    except json.JSONDecodeError as exc:
        print(f"ERROR: Invalid JSON in {path}: {exc}", file=sys.stderr)
        sys.exit(2)


def load_text(path: str) -> str:
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except FileNotFoundError:
        print(f"ERROR: File not found: {path}", file=sys.stderr)
        sys.exit(2)


def run_gate(story_id: str, out_dir: str, expected_count: int | None) -> list[str]:
    failures: list[str] = []

    json_path = os.path.join(out_dir, f"{story_id}_test-cases.json")
    md_path = os.path.join(out_dir, f"{story_id}_test-cases.md")

    # ── File existence ────────────────────────────────────────────────────────
    for path in (json_path, md_path):
        if not os.path.isfile(path):
            failures.append(f"Missing output file: {path}")

    if failures:
        return failures  # no point continuing if files are absent

    data = load_json(json_path)
    md_text = load_text(md_path)

    test_cases = data.get("test_cases", [])

    # ── Gate 1: story_id field present ────────────────────────────────────────
    if data.get("story_id") != story_id:
        failures.append(
            f"story_id mismatch: JSON has '{data.get('story_id')}', expected '{story_id}'"
        )

    # ── Gate 2: test_cases non-empty ─────────────────────────────────────────
    if not test_cases:
        failures.append("test_cases[] is empty")
        return failures  # remaining checks need at least one case

    # ── Gate 3: exact count when specified ───────────────────────────────────
    if expected_count is not None and len(test_cases) != expected_count:
        failures.append(
            f"Expected {expected_count} test cases, got {len(test_cases)}"
        )

    # ── Gate 4: per-case structural checks ───────────────────────────────────
    seen_ids: set[str] = set()
    for tc in test_cases:
        cid = tc.get("id", "<no id>")

        # Duplicate ids
        if cid in seen_ids:
            failures.append(f"{cid}: duplicate id")
        seen_ids.add(cid)

        # Required scalar fields
        for field in ("id", "scenario_id", "polarity", "risk"):
            if not tc.get(field):
                failures.append(f"{cid}: missing or empty '{field}'")

        # covers must be a list (may be empty for plan-derived)
        if "covers" not in tc:
            failures.append(f"{cid}: missing 'covers' field")
        elif not isinstance(tc["covers"], list):
            failures.append(f"{cid}: 'covers' must be a list")

        # polarity value
        polarity = tc.get("polarity", "")
        if polarity and polarity not in VALID_POLARITIES:
            failures.append(
                f"{cid}: invalid polarity '{polarity}' — must be one of {sorted(VALID_POLARITIES)}"
            )

        # risk value
        risk = tc.get("risk", "")
        if risk and risk not in VALID_RISKS:
            failures.append(
                f"{cid}: invalid risk '{risk}' — must be one of {sorted(VALID_RISKS)}"
            )

        # ≥1 precondition, step, assertion
        for section in ("preconditions", "steps", "assertions"):
            val = tc.get(section)
            if not val:
                failures.append(f"{cid}: '{section}' is missing or empty")
            elif not isinstance(val, list):
                failures.append(f"{cid}: '{section}' must be a list")

    # ── Gate 5: MD must not contain a JSON code block ────────────────────────
    if re.search(r"```json", md_text, re.IGNORECASE):
        failures.append("MD file contains an embedded JSON code block (forbidden by output-format.md)")

    # ── Gate 6: MD must be non-empty ─────────────────────────────────────────
    if not md_text.strip():
        failures.append("MD file is empty")

    return failures


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Stage 4 final-gate validator for manh-product-test-case-generator"
    )
    parser.add_argument(
        "--story-id",
        required=True,
        help="JIRA ticket id used as the file name prefix (e.g. OM-122824)",
    )
    parser.add_argument(
        "--out-dir",
        required=True,
        help="Directory containing the two output files",
    )
    parser.add_argument(
        "--expected-count",
        type=int,
        default=None,
        help="Exact number of test cases expected (omit to skip count check)",
    )
    args = parser.parse_args()

    failures = run_gate(args.story_id, args.out_dir, args.expected_count)

    json_path = os.path.join(args.out_dir, f"{args.story_id}_test-cases.json")
    md_path = os.path.join(args.out_dir, f"{args.story_id}_test-cases.md")

    if not failures:
        data = json.loads(open(json_path, encoding="utf-8").read())
        tc = data.get("test_cases", [])
        from collections import Counter
        polarities = Counter(c.get("polarity", "?") for c in tc)
        print(f"Test cases : {len(tc)}")
        print(f"Polarities : {dict(polarities)}")
        print()
        print("All final gate checks passed ✅")
    else:
        for f in failures:
            print(f"FAIL: {f}")
        sys.exit(1)


if __name__ == "__main__":
    main()
