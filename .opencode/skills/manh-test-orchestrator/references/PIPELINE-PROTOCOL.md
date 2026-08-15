# Test Orchestrator Pipeline Protocol

This document defines the four-stage execution pipeline for the generated
`[repo]-test-orchestrator` skill. All stages run deterministically — no prompts,
no gates. The plan file is the single input that drives all decisions.

---

## Pipeline Overview

```
STAGE 1 — PARSE PLAN
STAGE 2 — GAP ANALYSIS
STAGE 3 — SCENARIO DELEGATION  →  {testScenariosSkill} (diff mode)
STAGE 4 — TEST GENERATION      →  {testGeneratorSkill} (per gap)
─────────────────────────────────────────────────────────────────
OUTPUT   — Write validation-report.md + print verdict summary
```

---

## STAGE 1 — PARSE PLAN

Extract structured inputs from the plan file.

S1-A  Resolve plan file path from the `validate:` argument.
      - If path given: read directly
      - If ticket ID given: glob `plan/**/{ticketId}*.md`, pick latest by filename version suffix
      - If no argument: glob `plan/**/*.md`, pick highest mtime

S1-B  Read plan YAML header. Extract:
        planTicket   ← ticket:
        planType     ← type:
        planQuarter  ← quarter:
        planArea     ← functional_area:

S1-C  Extract Section 2 — Implementation Steps.
      Parse each numbered step. From each step extract:
        - File path(s) listed under "File:" or "Modifies:" (any explicit file references)
        - Class/method name if stated
      Build: `implementedFiles[]` — deduplicated list of Java/TS files changed

S1-D  Extract Section 3 — Test Plan.
      From 3a (Unit Tests):  `plannedUnitTestClasses[]` — class names + file paths
      From 3b (Component):   `plannedComponentTests[]`  — scenario names + file paths
      From 3c (Regression):  `regressionTable[]`        — existing test classes + action
      From 3f (Coverage):    `estimatedCoverage{}`      — estimated unit/component count, gate

S1-E  Extract Section 5 — Definition of Done.
      Collect all test-related DoD items (any checkbox containing "test", "coverage",
      "scenario", "regression", "validation", "logging review").
      Build: `dodTestItems[]` — the target checklist for Stage 2.

S1-F  Verify:
      If `implementedFiles[]` is empty:
        ```
        VALIDATION BLOCKED — plan file is missing required sections.
        Missing: Section 2 has no file references.
        Re-run {repoName}-change-planner to regenerate the plan with all required sections,
        or manually add the missing sections before invoking validate:.
        ```
        STOP.

      If `plannedUnitTestClasses[]` is empty AND `plannedComponentTests[]` is empty:
        WARN: "Section 3 has no planned test classes — gap analysis will be broad."
        Continue (do not block).

**Stage 1 output**: `planContext` object with all parsed fields.

---

## STAGE 2 — GAP ANALYSIS

Identify the actual test coverage gap — the delta between what Section 3 planned and
what exists or can be confirmed in the repo.

S2-A  For each file in `implementedFiles[]`:
        Derive expected test file path using the naming convention: `{namingConvention}`.
        Example: `ForecastDemandService.java` → `ForecastDemandServiceTest.java`

S2-B  Check existence of each expected test file:
        If tool supports file existence check (grep, ls, glob): verify file exists.
        If not: mark as "unverifiable — assumed missing".

S2-C  Build gap list:
        `gapFiles[]` = implementedFiles where test file does not exist or cannot be verified.

S2-D  Cross-reference `regressionTable[]` from Section 3c:
        For each row where action = "Will break: [fix required]":
          Add to: `regressionRisks[]` — tests that will fail and need fixing.

S2-E  Cross-reference `dodTestItems[]`:
        Mark each DoD test item as:
          ✅ Satisfiable — test file found or scenario covered by planned tests
          ⚠ Gap          — test file missing or scenario has no planned coverage
          ⏳ Unverifiable — file existence cannot be confirmed in this session

S2-F  Compute gap severity:
        If `gapFiles[]` is empty AND `regressionRisks[]` is empty:
          severity = **GREEN**  (coverage gap closed by planned tests)
        If `gapFiles[]` ≤ 2 AND `regressionRisks[]` is empty:
          severity = **YELLOW** (minor gap — targeted generation needed)
        If `gapFiles[]` > 2 OR `regressionRisks[]` is not empty:
          severity = **RED**    (significant gap — generation and regression fix needed)

**Stage 2 output**: `gapAnalysis` with `gapFiles[]`, `regressionRisks[]`, `dodStatus[]`, `severity`.

---

## STAGE 3 — SCENARIO DELEGATION

Invoke `{testScenariosSkill}` in diff mode for each gap file to get concrete
scenario lists — the "what to test" answer grounded in the repo's domain vocabulary.

S3-A  Check: is `{testScenariosSkill}` available in this session?

      **If YES** (delegation available — Devin CLI, Claude Code, OpenCode):
        For each file in `gapFiles[]`:
          Invoke `{testScenariosSkill}` with:
            mode: diff
            input: "File changed: {filePath} — context: {implementedChange from Section 2}"
          Collect output: `scenarioList[file]`

      **If NO** (Windsurf, Cursor, or skill not installed):
        Emit delegation panel:
        ```
        ┌──────────────────────────────────────────────────────────────┐
        │  DELEGATION REQUIRED — {testScenariosSkill} not available    │
        │                                                              │
        │  Run the following in a second step:                         │
        │                                                              │
        │  For each file below, invoke:                                │
        │  "{testScenariosSkill}: Based on changes to {filePath},      │
        │   identify scenarios (diff mode)"                            │
        │                                                              │
        │  Files needing scenarios:                                    │
        │  {gapFiles[] — one per line}                                 │
        └──────────────────────────────────────────────────────────────┘
        ```
        Set `scenarioList = {}` (empty — generation step will use broad template)

S3-B  If `scenarioList` is populated:
        For each file in `gapFiles[]`:
          `scenarioList[file]` is now the input for Stage 4 test generation.
      If `scenarioList` is empty:
        Stage 4 uses plan Section 3 as the test specification instead.

**Stage 3 output**: `scenarioList` map (file → scenario list, or empty map).

---

## STAGE 4 — TEST GENERATION

Invoke `{testGeneratorSkill}` for each gap to produce compilable test scaffolds.

S4-A  Check: is `{testGeneratorSkill}` available in this session?

      **If YES** (delegation available):
        For each file in `gapFiles[]`:
          Invoke `{testGeneratorSkill}` with:
            input: "Generate test for {filePath}.
                    Scenarios: {scenarioList[file] or Section 3 planned scenarios}.
                    Template: B-1 (always). Also B-2 if handler class. B-3 if component test."
          Collect output: `generatedTests[file]` — the test scaffold (compilable code)

      **If NO** (Windsurf, Cursor, or skill not installed):
        Emit delegation panel:
        ```
        ┌──────────────────────────────────────────────────────────────┐
        │  DELEGATION REQUIRED — {testGeneratorSkill} not available    │
        │                                                              │
        │  Run the following in a second step:                         │
        │                                                              │
        │  For each file below, invoke:                                │
        │  "Generate test for {filePath}" with {testGeneratorSkill}    │
        │                                                              │
        │  Priority order (from Stage 2 gap severity):                 │
        │  {gapFiles[] sorted by: regression risk first, then service  │
        │   layer, then handler, then REST controller}                 │
        └──────────────────────────────────────────────────────────────┘
        ```
        Set `generatedTests = {}` (empty — report records gap, not scaffold)

S4-B  For each `regressionRisk` in `regressionRisks[]`:
        Note: regression test fix is a manual engineering action, not auto-generatable.
        Record in report: "⚠ Regression risk: {testClass} needs fix — {reason from 3c}"

**Stage 4 output**: `generatedTests` map (file → scaffold), `regressionNotes[]`.

---

## OUTPUT — Write Validation Report

O-A  Compute output path:
       `plan/{category}/{quarter}/{planTicket}-validation.md`
     where category and quarter are derived from the resolved plan file path.

O-B  Write the validation report using the structure defined in
     [VALIDATION-REPORT-FORMAT.md](./VALIDATION-REPORT-FORMAT.md).

O-C  Print verdict summary to console:
```
┌────────────────────────────────────────────────────┐
│  VALIDATION COMPLETE — {planTicket}                │
│                                                    │
│  Severity : {GREEN | YELLOW | RED}                 │
│  Gaps found       : {N} files                      │
│  Scenarios added  : {N} (via {testScenariosSkill}) │
│  Tests generated  : {N} scaffolds                  │
│  Regression risks : {N}                            │
│                                                    │
│  Report: {output path}                             │
│                                                    │
│  DoD test items:                                   │
│  {dodStatus[] — one per line with ✅/⚠/⏳}         │
└────────────────────────────────────────────────────┘
```
