# Output Artifact Template

Used by `manh-issue-triage` in Stage 7 to generate the operator-facing triage result.

The artifact is written to `products/{product}/triage/{ticket-id}-triage.md` and
presented to the operator for review. The skill does **not** write to Jira or any
ticketing system — the operator decides what, if anything, to record there.

---

## Artifact structure

The artifact opens with a Quick Summary so the reader gets the full gist
immediately without reading the full report. All 7 sections are always present —
Section 4 is marked "Not applicable" when the verdict is not a defect. The
Information Request Package (Section 7) is present whenever any IRR item remains
open — including on runs that reached a successful classification but still have
outstanding good-to-have items. Only omit Section 7 when every data need was
resolved during the run.

---

```markdown
---
ticket-id: "{ticket-id}"
product: "{product}"
triage-date: "{YYYY-MM-DD}"
classification: "{Existing Defect|As Designed|Not a Product Defect|Not Supported / Future Enhancement|Inconclusive — {variant}|Needs monitoring / reproduction instrumentation|Cannot start}"
confidence: "{Low|Medium|High}"
resolution-path: "{expectation-correction|configuration-change|new-capability|base-code-fix|pending-human-review}"
iteration-cap-hit: {true|false}
---

# Triage Report — {ticket-id}

## Quick Summary

**Issue:** {one sentence — what is wrong, for which entity/transaction, in business terms}
**Verdict:** {classification label} — {Axis-B resolution-path tag}
**Confidence:** {Low / Medium / High}
**Next action:** {one sentence — what the operator should do right now}
**Outstanding data:** {None | {N} items needed — see Section 7}

---

## 1. Issue Summary

**Symptom (business terms):** {one-paragraph description of what the reporter says is wrong,
in business language — not technical jargon}

**Functional area:** {primary area} {/ secondary area if applicable}

**Environment:**
- Version / code-drop: {value or "not provided"}
- Stack / tenant: {value or "not provided"}

**Expected behavior:** {what the reporter says should happen}

**Actual behavior:** {what is happening}

{If error clues are present in the ticket:}
**Error clues:** {error codes, stack traces, or log snippets from the ticket}

---

## 2. Decision

| Field | Value |
|---|---|
| **Classification** | {label} |
| **Confidence** | {Low / Medium / High} ({score} — mechanically derived) |
| **Axis A — Cause** | {Base code does not behave per contract / Base code behaves correctly} |
| **Axis B — Resolution path** | {expectation-correction / configuration-change / new-capability / base-code-fix} |
| **Iteration cap hit** | {No / Yes — cap of {n} cycles reached; best hypothesis at current confidence} |

**Reason:** {structured reason per the classification label template in CLASSIFICATION-LOGIC.md}

**Predicate trail** — the confirmed predicates that drove this classification:

| Predicate | Question | Result | Evidence |
|---|---|---|---|
| {P1} | {the yes/no question} | {TRUE / FALSE} | {E1, E2} |
| {P2} | {the yes/no question} | {TRUE / FALSE} | {E3} |

**Evidence references** (from Section 10 — include only evidence cited in the predicate trail above):

| ID | Source | Locator | Fact | Reliability |
|---|---|---|---|---|
| {E1} | code | {ClassName}.java:{line-start}-{line-end} | {what the code does at this location} | {high\|provisional} |
| {E2} | docs | {doc title} > {section heading} | {specific rule or contract statement} | {high\|provisional} |
| {E3} | logs | {filename}, lines {n}-{m} \| {timestamp} \| trace: {id} \| {component} \| {level} | {key values — status, IDs, outcome} | {high\|provisional} |
| {E4} | customer_data | {config screen / API endpoint / entity type + ID} | {specific value or state observed} | {high\|provisional} |

{Evidence quality check — run internally, do not include in artifact:
- Locator is navigable: file+line (code), doc+section (docs), file+timestamp+trace (logs), screen/API (customer_data)
- Fact is a specific finding — not a description of the source
- Source classified by origin: log files → logs even if they contain customer data values
If any row fails: correct from session state Section 10 before writing.}

{If Inconclusive — include this block:}
**Why no label was committed:** {specific gap or tied hypotheses — what would resolve it}
**Remaining open hypotheses:** {H1 at {confidence} — blocked on P{n}; H2 at {confidence} — blocked on P{n}}
**Predicates still UNKNOWN:** {P3 — needs {datum}; P4 — needs {datum}}

---

## 3. Hypotheses Considered

{For each hypothesis evaluated, one block:}

### H{n}: {Cause class} — {one-line statement}

| Field | Detail |
|---|---|
| **Lifecycle status** | {proposed \| under_test \| confirmed \| refuted \| unknown \| discarded \| discarded-by-operator} |
| **Confidence** | {Low / Medium / High} ({score} — mechanically derived from predicate count) |
| **Governing predicates** | {P1, P2, ... — predicate_ids from Section 9 that test this hypothesis} |
| **Supporting evidence** | {E1, E2, ... — evidence_ids from Section 10 that confirm} |
| **Contradicting evidence** | {E3, ... — evidence_ids from Section 10 that refute, or "none"} |
| **Reasoning** | {why this hypothesis could explain the symptom} |
{If operator steer is non-null:}
| **Operator steer** | {"discarded by operator: {reason}" or "added by operator" or "reprioritized by operator: {reason}"} |

{Repeat for each hypothesis. List confirmed/under_test first; discarded/refuted last.}

**Hypotheses not pursued:** {any cause classes excluded early and why, or "all classes
considered"}

---

## 4. Defect Deep-Dive

{If classification is NOT Existing Defect:}
**Not applicable** — defect deep-dive is only populated for Existing Defect verdicts.
Classification for this ticket is {classification}.

---

{If classification IS Existing Defect, populate below.
This section is self-contained — the fix skill can consume it without re-running triage.}

**Deep-dive confidence:** {High — fault site verified against data record or confirmed log |
Medium — fault site identified in code; not yet validated against actual entity record |
Low — suspected area only; fix agent must verify before acting}

**Load-bearing assumptions this deep-dive rests on:** {list, or "none — all validated"}

---

### 4.1 Root Cause and Contract

**Code location(s):**

| Location | Verified | Note |
|---|---|---|
| {file / function / line range} | Yes | {confirmed reachable on execution path} |
| {file / function / line range} | No | Suspected area — {reason not verified; fix agent must confirm} |

**Fault condition:** {the exact condition under which the fault fires — the specific
input shape, data state, config value, or code path that triggers it; be precise
enough that the fix developer knows when the fault is active vs. inactive}

**What the code currently does:** {precise description of the actual behavior at the
fault location, including the observable data state that confirms the fault —
e.g. "CurrencyConversionRate is null on the ShipmentCost entity after mapRequest() completes"}

**What it should do instead:** {precise description of the correct behavior}

**Documented contract:**
- **Expected behavior:** {what the product documentation says should happen}
- **Source:** {doc title / Confluence page / module file + section}
- **How the code breaks it:** {specific way the code violates the documented contract}

---

### 4.2 Trigger Conditions

**Reproducibility:** {Deterministic — always reproduces given the inputs below |
Conditional — depends on specific data shape or environment state (describe)}

**Inputs that trigger the fault:**
- {input / data shape / config value — include minimum data required to reproduce}

**Feature flag / config state required:**
- {flag name}: {state required for fault to manifest, or "not applicable"}

**Execution path:**
{Ordered list of key steps from trigger to symptom — include queue/message type
and handler binding if the flow is AWPF/messaging-based:}
1. {service/function / queue / message type} → {action}
2. {service/function} → {action}
3. {fault location} → {wrong behavior — data state at this point}

**What was ruled out:** {cause classes or code paths explicitly eliminated during
triage and why — saves the fix developer from re-investigating discarded paths}

---

### 4.3 Scope and Verification

**Affected components:** {list of components involved}
**Blast radius:** {likely scope — single tenant / all tenants / specific config only /
specific data shapes only; include affected flows}

**Minimum steps to reproduce in a lower environment:**
1. {step}
2. {step}
3. {step}

---

### 4.4 Fix and Validation

**Minimal change:** {the specific operation or statement that addresses the fault —
precise enough for the fix skill to validate and implement, including the exact
file/function where the change is made; e.g. "add
target.setCurrencyConversionRate(source.getCurrencyConversionRate()) in
CostModificationRequestMapServiceImpl.mapRequest() after the existing field mappings"}

**Why this approach:** {brief reasoning — why fix here and not elsewhere; alternatives
considered during triage and why they were set aside}

**Guard conditions:** {whether the fix needs a null check, a conditional, or can be
applied unconditionally — and why}

**Feature flag needed:** {Yes — recommended to gate this change |
No — fault is unconditional and fix is safe to apply directly |
Recommended — change affects behavior for all tenants}

{If data or config migration is required:}
**Data / config migration:** {what needs to change and why}

**Side effects to watch:** {downstream components that consume the fixed field or
behavior; related flows that could be affected}

**Test scenarios to confirm fix:**
- {scenario description — inputs, expected result}

**Log / metric checks:**
- {what to look for in logs to confirm correct behavior}

**Regression scenarios:**
- {related flows to verify were not broken by the fix}

---

## 5. Completeness and Assumptions

**Completeness scorecard:**

| Input | Status | Note |
|---|---|---|
| {input name} | Present / Partial / Missing | {detail} |
| {input name} | Present / Partial / Missing | {detail} |

**Assumptions taken to proceed:**

{If no comment-derived assumptions — omit Source column:}
| ID | Assumption | Load-bearing | Stage made |
|---|---|---|---|
| A1 | {what was assumed and why} | Yes / No | {stage} |
| A2 | {what was assumed and why} | Yes / No | {stage} |

{If one or more comment-derived assumptions — include Source column:}
| ID | Assumption | Source | Load-bearing | Stage made |
|---|---|---|---|---|
| A1 | {what was assumed and why} | {operator-confirmed comment: {author}, {date} / analytical / operator-stated} | Yes / No | {stage} |
| A2 | {what was assumed and why} | {source} | Yes / No | {stage} |

{If no assumptions: "No assumptions required — all required inputs were present."}

{If comments or attachments were retrieved — always include this table:}
**Comments and attachments:**

| Item | Author | Date | Operator decision | Used as evidence in |
|---|---|---|---|---|
| {one-line summary} | {author} | {date} | Confirmed / Not used | {hypothesis IDs, or "—"} |

---

## 6. Next Steps

{Render only the block matching the verdict. Do not include other variants.}

{If Existing Defect:}
See defect deep-dive (Section 4). Hand off to fix skill — Section 4 is self-contained
and can be consumed without re-running triage.

{If As Designed:}
Return explanation to the operator. Expectation correction needed for the reporter.
Suggested response to reporter: "{one paragraph — what the product does, why it is correct,
what the reporter should do instead}"

{If Not a Product Defect:}
Return the specific factor and resolution to the operator:
- **Factor:** {config value / data condition / custom code location / environment detail}
- **Resolution:** {exactly what the operator or customer needs to do to resolve it}

{If Not Supported / Future Enhancement:}
Log an enhancement candidate for the product backlog.
- **Gap:** {what the product does not currently support}
- **Enhancement description:** {brief description of the desired capability}

{If any Inconclusive variant:}
{Provide specific, ordered next steps to progress the ticket based on the open hypotheses
and available evidence. Be concrete — name the exact action, what to look for, and what
the result means for the investigation.}

The full data request package — including all blocking and good-to-have items with
retrieval instructions — is in **Section 7** of this report.

{If Needs monitoring / reproduction instrumentation:}
The issue could not be reproduced or localized from available evidence. When the issue
next occurs, capture the following immediately:
- {specific log source, level, and time window}
- {specific metric or trace}
Full capture plan is in Section 7.

---

## 7. Information Request Package

{Present this section whenever any Information Request Register items remain unresolved
at the end of the run — regardless of classification outcome. Omit only when all items
were resolved during the run.}

### 7.1 Analysis Reference

Operator-facing context — what is needed, where to get it, and why it matters.
Items are **ranked by discriminating power** (highest impact first), then grouped
by priority. Each item maps to a specific entity (config entity, data record, log
source) — not individual fields. One item per entity.

**HARD RULE for "Where to get it" column:** list every retrieval path available
(API endpoint, UI screen path, log source). If code tracing only identified a
database or storage layer location, re-trace upward through the service layer to
find the API endpoint or UI screen that exposes the same data — check harness
artifacts (repo-analysis.md) for REST endpoints if available. Only cite a database
table if no API or UI path can be found after re-tracing, and mark it explicitly as
"internal — operator access only; do not forward to customer."

#### Blocking — Required to complete or confirm triage
*(ranked by discriminating_power descending)*

| # | Power | What is needed | Where to get it | Unblocks | Obtainability |
|---|---|---|---|---|---|
| 1 | {n predicates} | {entity name — e.g. "CarrierProfile config for tenant ACME"} | {API endpoint + method, or screen path} | {P1, P2, P3} | {operator-fetch \| relay-to-customer} |
| 2 | {n predicates} | {entity name} | {where} | {P4, P5} | {obtainability} |

{If any Blocking item requires non-operator access:}
**Access note:** item #{n} requires relay-to-customer — {who needs to provide it and how}.

#### Good-to-Have — Would improve confidence or completeness

| # | Power | What is needed | Where to get it | Why it helps |
|---|---|---|---|---|
| 1 | {n predicates} | {entity name} | {where} | {which assumption it would confirm or hypothesis it would strengthen} |

{If no outstanding items: "No outstanding information requests — all required inputs
were resolved during this triage run."}

{If Needs monitoring / reproduction instrumentation outcome:}

#### Capture Plan — When the issue next occurs

Capture the following at the moment the symptom appears:

| What to capture | Level / window | Why |
|---|---|---|
| {log source} | {log level, time window} | {what it reveals} |
| {metric / trace} | {granularity} | {what it reveals} |

---

### 7.2 Request to Reporter

{Generate a polite, plain-language block ready to paste as a JIRA comment.
Rules:
- Derived from 7.1 only — one ask per entity row in 7.1 Blocking table;
  do NOT split entities back into individual fields
- Include only Blocking and Good-to-have items — never Optional-not-requested
- **NEVER include database table names, SQL queries, or storage layer references —
  this block is sent directly to the customer. If 7.1 only has a database reference,
  describe the business entity by name (e.g. "the CarrierProfile record for tenant ACME")
  and ask the customer to provide it via their preferred access method.**
- Ask for data only — no rationale, no hypothesis references, no internal context
- Offer all retrieval paths for each item (API, UI, log) — reporter chooses
- Use polite language throughout — this gets posted directly on the ticket
- For API calls: include exact endpoint and request body
- For UI navigation: include exact screen path
- For logs: include the component service name and log level to enable
  (default DEBUG); class/method detail is for 7.1 only — omit here
- Open with "Could you please help us..." and close with "Thank you for your help."}

Could you please help us with the following to progress the investigation?

**Required:**

1. {entity name — e.g. "CarrierProfile configuration for tenant ACME"}
   - Option A: {API endpoint + method + key params}
   - Option B: {UI screen path — e.g. "Carrier Management > Tenant Config > Carrier Profile"}
   {Add Option C if a log source also surfaces this}

2. {next blocking entity if genuinely a separate fetch — omit if covered by item 1}

**If you are also able to provide the following, it would help us further:**

3. {good-to-have entity}
   - Option A: {retrieval path}
   - Option B: {alternative retrieval path}

Thank you for your help.
```
