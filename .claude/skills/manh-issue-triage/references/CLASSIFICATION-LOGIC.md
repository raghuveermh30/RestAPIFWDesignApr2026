# Classification Logic

Used by `manh-issue-triage` in Stage 6. Load this file just before Stage 6 begins.
Do not load it earlier — it is only needed at classification time.

---

## 1. When to classify

Classification is committed only **after Stage 5 revalidation**. No label is assigned
at intake, during scoping, or after hypothesis enumeration alone. "As designed"
is held to the same evidence bar as "defect" —
never used as early exits.

**As Designed and Not a Product Defect require positive evidence — they are not
defaults.** Do not assign either label because a defect hypothesis failed to reach the
classification threshold. Each non-defect label requires its own cited evidence:
- **As Designed** requires a governing spec or doc section that explicitly covers the
  scenario and supports the current behavior, plus confirmation no supported toggle
  changes it.
- **Not a Product Defect** requires the specific config value, data record, custom-code
  location, or environment factor that produces the symptom — not merely the absence
  of a code defect.
If the evidence for the non-defect label is absent or insufficient, emit Inconclusive
rather than defaulting to As Designed or Not a Product Defect.

---

## 2. Confidence thresholds — three distinct bars

Do not conflate these. Each serves a different gate.

| Threshold | Where used | What it means |
|---|---|---|
| **Exploration-stop threshold** | Gate G4 | Leading hypothesis is clearly ahead of runner-up → stop gathering evidence, move to revalidation. Deliberately ≤ classification threshold. A G4 pass does NOT guarantee classification will succeed. |
| **Skip-data threshold** | Gate G2b | Skip Stage 3 only when a hypothesis already meets the *classification* threshold from code + docs + ticket evidence alone AND non-defect causes can be excluded without customer data. This is the classification threshold, reached early. |
| **Classification threshold** | Stage 6 | The bar a leading hypothesis must clear to be committed as a final label. Must be **High confidence AND meaningfully ahead of runner-up**. If unmet at end of run → Inconclusive. |

### Mechanical confidence model (replaces narrative assertion)

Confidence is **derived** from the predicate ledger (session state Section 9) and
evidence records (Section 10). Never assert confidence — always compute it.

**Computation rule (apply at N4 Step 4.2 and again at N6):**

```
confirmed_predicates = count of predicates in Section 9 where:
  - result = TRUE or FALSE
  - this hypothesis is in tests_hypotheses

high_reliability_evidence = count of unique evidence_ids in evidence_refs of
  confirmed_predicates where reliability = high

provisional_evidence = count of unique evidence_ids where reliability = provisional
```

| Score | Label | Condition |
|---|---|---|
| 0.7–1.0 | **High** | ≥ 2 confirmed predicates AND ≥ 2 independent high-reliability evidence records |
| 0.4–0.7 | **Medium** | 1 confirmed predicate with ≥ 1 high-reliability evidence record, OR ≥ 2 confirmed predicates where some evidence is provisional |
| 0.0–0.4 | **Low** | Only proposed/UNKNOWN predicates, OR all supporting evidence is provisional |

**Independence rule:** two evidence records are independent if they come from
different `source_type` values (e.g. one `code` + one `customer_data`), or from
different locations within the same source type (different files/functions).
Evidence records from the same location re-read in N5 count as one, not two.

**"Meaningfully ahead"** = leading hypothesis `confidence-score` at least 0.2
above runner-up's score.

---

## 3. Two-axis model

Score on two independent axes before labeling.

**Axis A — Cause:**
Does base product code behave per its documented contract for the given inputs and
configuration state?
- **No** → Existing Defect
- **Yes** → As Designed, Not a Product Defect, or Not Supported (resolve via Axis B)

**Axis B — Resolution path:**
What actually delivers the customer's desired outcome?
- Nothing — expectation correction needed → As Designed
- A supported configuration change → Not a Product Defect (Configuration)
- A new capability (not achievable by any supported config) → Not Supported / Future Enhancement
- A base code fix → Existing Defect

Always attach the Axis-B resolution-path tag as a secondary label on the artifact,
even when the primary label is debatable — so the operator always has an unambiguous
next action regardless of classification debate.

---

## 4. The five final labels

### 4.1 Existing Defect

**Axis A:** No — base code does not behave per documented contract.
**Evidence bar:** validated root cause with a code location and the violated contract.
Code location must be verified in Stage 5 — if unverified, label it "suspected area"
and do not hand it to the fix skill as confirmed.
**Reason template:** "Base code at {file/function} produces {wrong behavior} under
{conditions}, violating {documented contract from doc/section}."
**Data stance:** may be labeled on code/docs evidence alone when the fault is
unconditional; record "valid inputs/config assumed" as a stated assumption. When
customer data is available, validate against it before committing.
**Next action:** only this label (and a confirmed regression from the regression gate)
proceeds to the fix skill, accompanied by the defect deep-dive (Section 4 of
OUTPUT-ARTIFACT-TEMPLATE.md).

---

### 4.2 As Designed

**Axis A:** Yes — current behavior matches the documented contract.
**Axis B:** No supported configuration changes the behavior; expectation is misaligned.
**Evidence bar:** governing spec/doc section + reconciliation showing claim ≠ documented
expectation + confirmation no supported toggle exists.
**Reason template:** "Behavior matches documented design in {doc/section} because
{rule}; no supported configuration alters this."
**Common for:** status rules, archive semantics, cancellation contracts, tax-override
rules, documented edge-case behaviors.

---

### 4.3 Not a Product Defect

Sub-types: Configuration / Data / User Error / Environment / Custom Extension.
**Axis A:** Yes — base product behaves correctly given the actual input.
**Evidence bar:** the specific config value, data record, custom-code location, or
environment factor that produces the symptom.
**Reason template:** "Caused by {factor — config value / data condition / custom code
location / environment detail}; base product behaves correctly given this input;
resolved by {specific config change / data correction / SOP correction / env fix}."

---

### 4.4 Not Supported / Future Enhancement

**Axis A:** Yes (no base code fault) but no supported configuration achieves the
desired outcome.
**Evidence bar:** absence of the capability in docs and code + confirmation the scenario
is an unsupported edge case or data shape.
**Reason template:** "Scenario {X} is outside current supported behavior; candidate
enhancement."
**Common for:** under-documented edge cases, unsupported data shapes, legitimate gaps
where the product simply does not cover the scenario.

---

### 4.5 Inconclusive

If no label reaches the classification threshold, do **not** force one.
Emit one of these Inconclusive variants:

| Variant | When |
|---|---|
| Inconclusive — insufficient information | A decision-blocking gap remains and no safe assumption bridges it |
| Inconclusive — genuine ambiguity | Multiple hypotheses remain credibly tied after iteration budget |
| Inconclusive — iteration cap hit | Loop budget exhausted before a leader firmed up |
| Needs monitoring / reproduction instrumentation | Non-reproducible; cause not localizable from current evidence |
| Cannot start | G1a floor not met: symptom + area signal + anchor entity absent |

Every Inconclusive and Cannot-start outcome **must** emit the Information Request
Package — the run never ends with "not enough info" without telling the operator
precisely what to go get and from where.

---

## 5. Config decision sub-tree

When the symptom is config-adjacent — including "I didn't set the config but expected
it to work" — resolve on a single question:

> **Does a supported, documented configuration exist that produces the desired behavior?**

1. **Yes, and customer hasn't set it / set it wrong**
   → **Not a Product Defect (Configuration)**
   The unset behavior is by-design, but the actionable resolution is a config change.
   Deciding evidence: existence of the supported toggle.

2. **No toggle exists; current behavior is documented; expectation contradicts the contract**
   → **As Designed**

3. **No toggle exists; behavior is unspecified either way; expectation is reasonable**
   → **Not Supported / Future Enhancement**

**Precedence rule for unset config:** documented default/unset behavior is As Designed
only when no supported toggle changes it. The moment a documented toggle exists, it
flips to Not a Product Defect (Configuration) — because the customer can self-resolve.

In all three branches, record the Axis-B resolution path so the next action is explicit.

---

## 6. Non-reproducible and intermittent issues

Some symptoms — especially environment/platform/timing/counter/replication/infra cause
class — will not reproduce deterministically.

- If evidence (logs, stack traces, data state) points to a base-code fault even without
  a deterministic repro: record an **evidence-only defect hypothesis at capped
  confidence**. Label Existing Defect only if classification threshold is met on
  evidence alone; otherwise it remains a leading hypothesis.
- If evidence is insufficient to localize the cause: emit
  **Needs monitoring / reproduction instrumentation** — name the specific logs, metrics,
  or traces to capture when the issue next occurs, and add them to the Information
  Request Package.
- Always state explicitly that the verdict rests on non-reproducible evidence and what
  would raise confidence.

---

## 7. Terminal outcomes — complete reference

Every run ends in exactly one of these outcomes.

| Outcome | When assigned | Operator receives |
|---|---|---|
| **Existing Defect** | Classification threshold met; base-code fault localized and verified | Full artifact + defect deep-dive; handed off to fix skill |
| **As Designed** | Behavior matches documented contract; no supported toggle changes it | Artifact with documented rule and expectation correction |
| **Not a Product Defect** | Customer-side factor explains the behavior | Artifact with specific factor and resolution |
| **Not Supported / Future Enhancement** | Legitimate gap; not achievable by any supported config | Artifact with enhancement note |
| **Inconclusive — insufficient information** | Decision-blocking gap; no safe assumption bridges it | Best hypotheses at capped confidence + Information Request Package |
| **Inconclusive — genuine ambiguity** | Multiple hypotheses credibly tied after iteration budget | Ranked ledger + what evidence would disambiguate each branch |
| **Inconclusive — iteration cap hit** | Loop budget exhausted before leader firmed up | Current best hypothesis + cap-hit note + what was still being chased |
| **Needs monitoring / reproduction instrumentation** | Non-reproducible; cause not localizable from current evidence | Concrete capture plan (logs/metrics/traces) added to Information Request Package |
| **Cannot start** | G1a triage-readiness floor not met | Specific missing floor item + obtainability-checked request to operator |
