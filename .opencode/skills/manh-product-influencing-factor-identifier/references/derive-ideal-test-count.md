# DeriveIdealTestCount — Reference Algorithm

This algorithm computes `combo_budget` and the full test-count breakdown from first
principles — behavior rules extracted from the plan and (optionally) the code diff.
Run it in **Phase 4c Step 1** instead of applying the static default table.

---

## Signature

```
ALGORITHM DeriveIdealTestCount(plan, code_diff)

INPUT:
  plan          — the plan file loaded in Phase 0
  code_diff     — optional for plan-only estimate; required for final accuracy

OUTPUT:
  factors                  — refined factor set with valid values
  constraints              — PICT constraint strings (double-quoted)
  interaction_obligations  — coverage obligations by interaction order (1-way, 2-way, k-way)
  standalone_seeds         — behavioral seeds that PICT alone cannot cover
  factor_weightage         — each factor's share of required coverage obligations (%)
  ideal_test_count         — minimum rows to cover all obligations + mandatory seeds
```

---

## Steps

### Step 1 — Extract Behavior Rules

Sweep **every section of the plan** before extracting rules. Do not limit extraction
to the AC section alone — plans with an explicit test matrix, integration points
section, or risk section carry significant behavioral surface in those sections.

**Required sweep order:**
1. Acceptance criteria / functional requirements (numbered AC lines)
2. Design / scope / confirmed behavior section (condition → outcome statements)
3. Test surface / test matrix section — each numbered scenario group is a rule cluster;
   extract one rule per distinct behavioral obligation in the group
4. Integration points section — each named integration path is a rule
5. Risk / edge-case section — each risk → recovery pair is a rule
6. State / data considerations — each visibility or state dependency is a rule

Extract every statement that has one of these forms:

| Pattern | Example signal |
|---|---|
| condition → outcome | "when flag disabled → action hidden" |
| state → transition | "committed item → cannot deselect" |
| input → payload/mutation | "quantity change → PATCH body includes delta" |
| failure → recovery/rollback | "network error → cart restored to pre-edit state" |
| visibility / gating rule | "entry point visible only when grant present" |
| required scenario | numbered item in a test matrix / scenario list |
| explicit regression requirement | AC line referencing a prior defect or known edge case |

**Ignore:** raw file count, LOC, commit size. These have no predictive value for
behavioral coverage.

**Under-extraction warning:** if a plan has 9+ scenario groups in its test matrix
and you extracted fewer than 15 rules total, re-sweep — you almost certainly missed
the integration points and risk sections.

---

### Step 2 — Normalize Each Rule

For every extracted rule, derive:

| Attribute | Description |
|---|---|
| `controls` | Tester-settable inputs referenced by the rule |
| `outcome` | Observable UI state, API payload, store mutation, or side effect |
| `mutation` | `add` / `update` / `void` / `no-change` / `rollback` (if applicable) |

---

### Step 3 — Create Factors

For each `control` identified in Step 2:

- Derive only values that execute **distinct behavior** (different branch, different
  payload, different guard outcome).
- If two values reach the **same branch and same outcome** → collapse them to one
  representative value.
- Retain the original values as `standalone_seeds` only if each must be verified
  individually for regression (e.g. explicit AC item per value).
- If a control has **only one valid value** → move it to `fixed_context`, not
  `factors`. Do not include fixed-context controls in the PICT model.

---

### Step 4 — Create Validity Constraints

For each extracted rule, if a value is **logically impossible** when another factor
takes a specific value, add a constraint:

```
IF [FactorA] = "value" THEN [FactorB] <> "impossible-value";
```

All constraint strings use PICT double-quoted syntax.

---

### Step 5 — Create Coverage Obligations

For each normalized rule, identify `referenced_factors` = the controls that jointly
affect the rule's `outcome` or `mutation`:

| referenced_factors count | Obligation created |
|---|---|
| 0 | Standalone seed / assertion (not a PICT tuple) |
| 1 | 1-way obligation for that factor value |
| ≥ 2 | k-way interaction obligation for those exact factors, where k = count |

**Do not default to pairwise or 3-way.** Interaction strength is derived solely from
the rule's actual dependencies. Over-ordering inflates the suite; under-ordering
misses interactions that the code genuinely joins.

---

### Step 6 — Split Orthogonal Features

Build a dependency graph:

- **Nodes** = factors
- **Edge(A, B)** = a coverage obligation references both A and B

Each **disconnected component** of the graph is an independent test surface.
Generate a separate suite per component. **Do not cross-product disconnected
components** — doing so multiplies test count with zero coverage gain.

---

### Step 7 — Build Valid Coverage Universe

```
U = all valid tuples required by interaction_obligations
    after applying constraints from Step 4
```

Each tuple in U represents one behavior that must be covered at least once.

---

### Step 8 — Generate Candidate Test Rows

```
candidate_rows = all valid assignments of factor values
                 that satisfy the constraints
```

For each candidate row:

```
covered(row) = set of tuples in U satisfied by that row
```

---

### Step 9 — Minimize the Suite

Solve a **set-cover** problem:

> Select the smallest set of `candidate_rows` such that every tuple in U is
> covered at least once.

```
core_test_count = |selected rows|
```

**Solver labeling:**

| Solver used | Label the result as |
|---|---|
| Exact (ILP / exhaustive) | `ideal_test_count` |
| Greedy / PICT heuristic | `generated_test_count` (not `ideal_test_count`) |

When PICT is used (the normal case for this skill), the result is
`generated_test_count`. Report it honestly. `ideal_test_count` is the
theoretical lower bound; `generated_test_count` is the achievable approximation.

---

### Step 10 — Add Standalone Seeds

`standalone_seeds` includes everything PICT combinations cannot guarantee:

- Logging / observability assertions
- Localization assertions (new user-visible strings)
- Label / text / UI layout checks
- Fixed-context checks (single-value factors from Step 3)
- Individual gate causes that share the same UI outcome (same-branch negatives)
- Visual / layout checks
- Coverage traded away during factor collapse (Step 3 original values)
- Explicit regression checks not represented by any tuple in U

```
final_test_count = core_test_count + count(standalone_seeds)
```

---

### Step 11 — Calculate Factor Weightage

For each factor `f`:

```
demand(f) = count of obligations in U that contain factor f
          + count of standalone seeds anchored to factor f

weight(f) = demand(f) / sum(demand(all factors)) * 100
```

For each value `v` of factor `f`:

```
value_weight(f, v) = count of obligations/seeds requiring (f = v)
                   / sum(demand(all factors)) * 100
```

Weightage shows which factors drive the most behavioral coverage and should be
prioritized when the suite must be cut further.

---

### Step 12 — Report

Return all of the following:

| Output field | Contents |
|---|---|
| `factors` | Final factor list with valid values (post-collapse) |
| `constraints` | All PICT constraint strings |
| `interaction_obligations` | Grouped by order: 1-way / 2-way / 3-way / … |
| `independent_components` | Disconnected graph components (separate test surfaces) |
| `factor_weightage` | Per-factor and per-value weight percentages |
| `core_test_count` | Rows from the minimized set-cover |
| `standalone_seed_count` | Count of standalone seeds |
| `final_test_count` | `core_test_count + standalone_seed_count` |
| `solver_status` | `"exact"` or `"generated"` |

---

## Using This Algorithm for `combo_budget`

`combo_budget` is the ceiling for the PICT core matrix (`core_test_count` /
`generated_test_count`). Derive it as follows:

```
combo_budget = core_test_count   (from Step 9, using plan rules)
```

When `code_diff` is available, recompute after Step 2 with diff-extracted rules
merged into the rule set — this typically tightens the budget by removing rules
the diff proves are unreachable.

**Relationship to the static defaults.** The static fallbacks (40 / 60 / 80 based
on factor count) are coarse proxies used only when the plan lacks sufficient
detail to run Steps 1–9. Prefer this algorithm whenever the plan has explicit AC
items, behavior rules, or risk/edge-case sections — those are the raw material
for Step 1.

**Final rule (invariant — never override):**

```
ideal_test_count  =  minimum constrained coverage rows
                  +  mandatory standalone seeds

factor_weightage  =  each factor's actual share of required coverage obligations

No fixed points.
No LOC / file-count scoring.
No static "70 test" target.
```

---

## Integration with Phase 4c

After running this algorithm, map its outputs to the Phase 4c fields:

| Algorithm output | Phase 4c / JSON field |
|---|---|
| `core_test_count` | `combo_est` (when solver = generated) or `ideal_test_count` annotation |
| `final_test_count` | `practical_total.total` |
| `standalone_seed_count` | `practical_total.seeds` |
| `combo_budget` (derived) | `combo_budget` field in emitted JSON |
| `factor_weightage` | Surfaced in the Phase 4c budget status line |
| `constraints` | `factors[].pict_constraint` strings |
| `interaction_obligations` | Informs PICT strength-N model in Stage 2 |
| `independent_components` | Each component → separate PICT model in Stage 2 |
