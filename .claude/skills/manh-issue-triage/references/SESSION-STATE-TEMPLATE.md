# Session State Template

Written by `manh-issue-triage` after each stage gate to
`products/{product}/triage/.session-{ticket-id}.md`.
Allows a paused session to be resumed with full accumulated state intact.

---

## File format

```yaml
---
ticket-id: "{ticket-id}"
product: "{product}"
session-key: "{slug}"
stage-reached: {0-8}
stage-status: "{in-progress|gate-passed|paused|complete}"
iteration-count: {n}
iteration-cap: 5
operator-extended-budget: {null|n}
started: "{YYYY-MM-DD HH:MM}"
last-updated: "{YYYY-MM-DD HH:MM}"
controller:
  current-node: "{N0|N1|N2a|N2b|N2c|N2d|N3|N4|N4.5|N5|N6|N7|N8}"
  last-completed-node: "{node-id or null}"
  last-completed-output-type: "{IntakeRecord|ScopingRecord|DocRecord|LocalizationRecord|ReconciliationRecord|HypothesisSketch|DataValidationRecord|HypothesisLedger|OperatorSteer|RevalidationRecord|ClassificationRecord|ArtifactRecord|HandoffRecord|null}"
  pre-router-active: false
---
```

---

## Section 1 — Ticket Summary (written at Stage 0)

```
symptom-summary: |
  {one-paragraph business-terms summary of what the reporter says is wrong}

expected-behavior: |
  {what the reporter says should happen}

actual-behavior: |
  {what the reporter says is happening}

environment:
  version: "{version/code-drop if known, else 'not provided'}"
  stack: "{cloud/on-prem/env name if known}"
  tenant: "{tenant/customer if known}"

causal-candidates:
  - candidate: "{component, flow, or causal connection as stated in the input}"
    source: "{ticket description | comment: {author}, {date} | attachment: {filename} | operator note}"
    type: "{observed-trigger | causal-interpretation}"
    promoted-by-operator: {false|true}

repro-steps: |
  {numbered steps if provided, else 'not provided — use for investigation only; omit from artifact if absent'}

error-clues: |
  {error codes, stack traces, log snippets extracted from ticket}

business-impact: "{System Outage|Work Stoppage|Critical|High|Medium|Low} — {impact description}"
workaround: "{workaround if any, else 'none reported' — retain internally; omit from artifact}"
```

---

## Section 2 — Scoping (written at Stage 1)

```
functional-area:
  primary: "{area name}"
  secondary: ["{area name}", "{area name}"]
  operator-confirmed: {true|false}
  operator-corrections: |
    {any corrections or additions the operator made, else 'none'}

repos-identified:
  - name: "{repo name}"
    path: "{local path or 'not cloned'}"
    source: "{repos.md|module-index inference|operator-provided}"
  - name: "{repo name}"
    path: "{local path or 'not cloned'}"
    source: "{repos.md|module-index inference|operator-provided}"

modules-loaded:
  - "{products/{product}/modules/{area}.md}"
  - "{products/{product}/module-index.md}"

completeness-scorecard:
  - item: "{input name}"
    status: "{Present|Partial|Missing}"
    blocking: {true|false}
    note: "{any relevant detail}"
```

---

## Section 3 — Assumptions Register (updated throughout)

Each assumption is labeled and flagged as load-bearing if a later contradiction
would change the classification.

```
assumptions:
  - id: "A1"
    statement: "{what was assumed}"
    reason: "{why the assumption was made — what data was missing}"
    load-bearing: {true|false}
    stage-made: {0-8}
    contradicted: {false|"{stage N: contradicted by X"}
  - id: "A2"
    ...
```

---

## Section 4 — Hypothesis Ledger (updated at Stage 4, 4.5, 5)

### Hypothesis lifecycle (Change B — hunch rule)

Every hypothesis moves through this lifecycle. **No hypothesis may influence
classification while in `proposed` status.** A hypothesis must reach `confirmed`
or `refuted` via verified predicates before it can drive or block a label.

```
proposed        → hypothesis exists; no predicate yet evaluated against it
                  CANNOT influence classification; CANNOT enter the defect deep-dive
under_test      → at least one predicate is being evaluated against it
                  CANNOT influence classification until a predicate resolves
confirmed       → at least one predicate resolved TRUE supporting this hypothesis,
                  backed by high-reliability evidence
                  MAY drive classification if it meets the confidence threshold
refuted         → at least one predicate resolved FALSE excluding this hypothesis
                  CANNOT drive classification; retained in ledger for audit
unknown         → predicates evaluated but all remain UNKNOWN (no evidence available)
                  treated as proposed for classification purposes — does not drive a label
discarded       → excluded by evidence during investigation (may not be reclassified)
discarded-by-operator → operator explicitly discarded at N4.5 seam; retained for audit
```

**Lifecycle transition rules (controller-enforced):**
- N2d: all hypotheses from initial generation enter as `proposed`
- N2b, N3, N4, N5: **discovery entry** — any node may add a new hypothesis at
  `proposed` when evidence encountered during that node's work suggests a cause
  class not currently in the ledger. Discovery-entered hypotheses are identical
  to N2d-entered ones: they must generate a predicate and be verified before
  influencing classification. Set `operator-steer: null` to distinguish from
  operator-added hypotheses.
- N2d→N3/N4: any hypothesis with at least one predicate being evaluated → `under_test`
- N3/N4/N5: when a predicate resolves TRUE → hypothesis → `confirmed`
- N3/N4/N5: when a predicate resolves FALSE eliminating the cause class → `refuted`
- N5 end: any hypothesis whose governing predicates are all UNKNOWN → `unknown`
- N4.5: operator discard → `discarded-by-operator` (evidence retained, not deleted)

```yaml
hypotheses:
  - id: "H1"
    cause-class: "{base-code|configuration|data|custom-extension|environment-platform|user-error|as-designed}"
    statement: "{what this hypothesis claims is the root cause}"
    confidence: "{Low|Medium|High}"
    confidence-score: {0.0-1.0}
    lifecycle-status: "{proposed|under_test|confirmed|refuted|unknown|discarded|discarded-by-operator}"
    governing-predicates: ["{P1}", "{P2}"]
    supporting-evidence: ["{E1}", "{E2}"]
    contradicting-evidence: ["{E3}"]
    reasoning: "{why this hypothesis could explain the symptom}"
    operator-steer: "{null|'discarded by operator: {reason}'|'added by operator'|'reprioritized by operator'}"
    stage-last-updated: "{N2d|N3|N4|N4.5|N5}"

  - id: "H2"
    ...
```

---

## Section 5 — Information Request Register (updated throughout)

Every gate that identifies a missing input records it here. Consolidated into the
ranked Information Request Package at the end of the run per Change F rules.

**IRR write rule:** every UNKNOWN predicate in Section 9 that cannot be resolved
from available evidence must have a corresponding IR entry in this section. The
`blocked_predicate` field links the two — Change F's IRP ranking is driven by
this link, not by heuristics.

```yaml
information-requests:
  - id: "IR1"
    what: "{exact data or artifact needed — specific, not generic}"
    type: "{config|transactional_data|log|screenshot|environment|api_response}"
    where_to_source: "{concrete: config screen/path | API operation + key params |
                       log source + trace + window | data entity + field;
                       if unknown, describe the KIND of source — never fabricate endpoints}"
    why_needed: "{which predicate this resolves AND which classification/confidence
                  step it unlocks — cite predicate_id}"
    blocked_predicates: ["{predicate_id}", "{predicate_id}"]
    discriminating_power: "{total number of predicates this entity/datum resolves —
                            sum across all grouped predicates; used for IRP ranking;
                            higher = ask first}"
    priority: "{Blocking|Good-to-have|Optional-not-requested}"
    obtainability: "{operator-fetch|relay-to-customer}"
    stage-raised: "{N0|N1|N2a|N2b|N2c|N2d|N3|N4|N5}"
    resolved: {false|"{node: resolved by X}"}

  - id: "IR2"
    ...
```

**`priority` values (Change F):**
- `Blocking` — required to move a classification or close an UNKNOWN predicate that
  blocks the leading hypothesis
- `Good-to-have` — would raise confidence or validate a load-bearing assumption but
  is not required to commit a label
- `Optional-not-requested` — negligible decision impact; recorded but NOT included
  in the IRP request to the reporter — do not inflate the ask

---

## Section 6 — Gate Decisions Log (updated at each gate)

```
gates:
  - gate: "G0"
    outcome: "continue"
    note: "{any relevant detail}"

  - gate: "G1a"
    outcome: "{pass|blocked}"
    note: "{any relevant detail}"

  - gate: "G1b"
    outcome: "{PASS|PARTIAL|FAIL}"
    missing-items: ["{item}", "{item}"]
    assumptions-taken: ["A1", "A2"]
    note: "{any relevant detail}"

  - gate: "G2"
    outcome: "{continue|doc-gap-flagged}"
    reconciliation-summary: "{one line}"
    note: "{any relevant detail}"

  - gate: "G2b"
    outcome: "{skip-stage-3|enter-stage-3|proceed-on-hypotheses}"
    reason: "{one line}"

  - gate: "G3"
    outcome: "{validated|reduced-confidence|not-entered}"
    note: "{any relevant detail}"

  - gate: "G4"
    outcome: "{leading-hypothesis-identified|loop-back|budget-exhausted}"
    leading-hypothesis: "{H1|null}"
    iteration-count-at-gate: {n}

  - gate: "G4.5"
    outcome: "auto-proceeded"
    operator-steer-received: {true|false}
    steer-applied: "{null|description of steer applied}"
    operator-steer:
      type: "{null|discard|reprioritize|add|supply-fact|promote-causal-candidate}"
      content: "{null|what the operator said or supplied}"
      timestamp: "{YYYY-MM-DD HH:MM or null}"
      applied: {true|false}

  - gate: "G5"
    outcome: "{survived|disconfirmed|budget-exhausted}"
    note: "{any relevant detail}"
```

---

## Section 7 — Reconciliation Table (written at Stage 2)

```
reconciliation:
  reported-claim: |
    {what the reporter says happened, in their terms}

  documented-behavior: |
    {what the product documentation says should happen}
    source: "{doc title / Confluence page / module file + section}"

  actual-code-behavior: |
    {what the code actually does, from tracing the execution path}
    source: "{file/function/line range or 'suspected area: {reason}' if unverified}"

  reconciliation-verdict: "{aligned|claim-contradicts-docs|claim-contradicts-code|docs-silent|docs-contradictory}"
  documentation-gap-flagged: {true|false}
  documentation-gap-note: "{description if flagged}"
```

---

## Section 8 — Stage 5 Revalidation Record (written at Stage 5)

```
revalidation:
  leading-hypothesis: "{H1}"
  code-locations-verified:
    - location: "{file/function/line range}"
      verified: {true|false}
      note: "{confirmed reachable on execution path | suspected area: {reason}}"
  contract-violated: |
    {the documented/intended behavior the code breaks}
    source: "{doc reference}"
  hypothesis-survived: {true|false}
  disconfirmation-note: "{null|what disconfirmed it}"
  predicate-resolution-summary: "{n} predicates confirmed TRUE/FALSE; {n} remain UNKNOWN"
  cap-hit: {false|true}
```

---

## Section 9 — Predicate Ledger (written starting at N2d; updated through N5)

A predicate is a single checkable yes/no/unknown question that, when answered,
moves one or more hypotheses. The model generates predicates freely — full
interpretive freedom in deciding which checks matter — but each predicate must
name its `source_required` and `locator` before it can move a classification.

**Hard rule:** a predicate with `result: UNKNOWN` cannot move a classification.
It must either be resolved against code/logs/data, or it becomes an IRP item (Change F).

```yaml
predicates:
  - predicate_id: "P1"
    question: "{a yes/no/unknown question whose answer moves one or more hypotheses}"
    tests_hypotheses: ["{H1}", "{H2}"]
    source_required: "{docs|code|logs|customer_data}"
    locator: "{doc section | code symbol (file/function/line) | log query | config screen | data entity}"
    result: "{TRUE|FALSE|UNKNOWN}"
    moves_on_result:
      TRUE: "{e.g. confirms H1 → advance to classification | next predicate P3}"
      FALSE: "{e.g. refutes H1 → H2 becomes leading}"
      UNKNOWN: "{e.g. emit IRP item IR{n} — what to fetch to make it checkable}"
    evidence_refs: ["{E1}", "{E2}"]
    stage-generated: "{N2d|N3|N4|N5}"
    stage-resolved: "{N2d|N3|N4|N5|null}"

  - predicate_id: "P2"
    question: "..."
    tests_hypotheses: ["{H2}"]
    source_required: "{docs|code|logs|customer_data}"
    locator: "..."
    result: "{TRUE|FALSE|UNKNOWN}"
    moves_on_result:
      TRUE: "..."
      FALSE: "..."
      UNKNOWN: "..."
    evidence_refs: []
    stage-generated: "{node}"
    stage-resolved: "null"
```

---

## Section 10 — Evidence Records (written at every node that fetches a fact)

Every fetched fact becomes a typed record. No free-text paragraphs — slot-fill only.
Reliability is set by the node, not inferred later.

**Trust / adjudication order (fixed):** code > customer_data > docs > logs.
When sources disagree, code wins. This order governs which evidence record
resolves a predicate when two records conflict.

**Starting-point / anchoring order (opportunistic — separate from trust order):**
the investigation may legitimately *begin* from any source that carries signal
(log, error signature, config artifact, doc). Starting from a log does not mean
trusting it as proof — the lead is confirmed in code per the trust order.

```yaml
evidence-records:
  - evidence_id: "E1"
    source_type: "{docs|code|logs|customer_data}"
    locator:
      code:          "{ClassName}.java:{line-start}-{line-end} — or {ClassName}.{method}() if line numbers unavailable"
      docs:          "{document title or file path} > {section heading}"
      logs:          "{filename}, lines {n}-{m} | {timestamp} | trace: {id} | {component} | {level} — omit absent fields"
      customer_data: "{config screen path / API endpoint / entity type + ID}"
    extracted_fact: |
      Fill based on source_type:
      code:          what the code does at this location — specific behavior, condition, or logic
      docs:          the specific rule, contract, or statement — not a description of the doc
      logs:          key values (status codes, entity IDs, costs, outcome) — not verbatim, not vague
      customer_data: the specific value or state observed (flag=DISABLED, field=null, value=5557.72)
    supports: "{predicate_id and/or hypothesis_id this confirms, and direction: confirms|refutes}"
    refutes: "{predicate_id and/or hypothesis_id this contradicts, or null}"
    reliability: "{high|provisional}"
    reliability_note: "{null | reason reliability is provisional — e.g. log provenance unconfirmed, doc may be stale}"
    stage-written: "{N2a|N2b|N2c|N2d|N3|N4|N5}"
```

**source_type classification:** classify by where the data came from, not what it contains.
- `code` — local repo files
- `docs` — product module files, Confluence, design docs
- `logs` — any log file (CSV, Kibana export, txt dump) — even when it contains
  customer data values. Log provenance rules apply regardless of content.
  Customer data values found in logs belong in `extracted_fact`, not `source_type`.
- `customer_data` — operator-provided directly: config screen, API response,
  data record, feature flag state

**Log evidence record rules (apply when `source_type: logs`):**
- `locator` — extract available fields from the log source, omitting any that are
  not present:
  `{filename}, lines {n}-{m} | {timestamp} | trace: {trace_id} | {component_name} | {class} | {log_level}`
- `extracted_fact` — state the key data points from the log entry that answer the
  predicate (values, entity IDs, outcome, status codes, method name if visible).
  Not verbatim full message, not a vague paraphrase.
  Good: "NPE at ChangeEventManager.java:567, SelectedLocation=null during TND_SHP_TenderUpdate"
  Good: "Rerate table for R11_ATOM_SHTL {14001 41001}: 24+ resources, identical costs (5557.72)"
  Bad: "log shows event was triggered" (too vague)
  Bad: full 200-character log line (too verbose)
- If timestamp or trace_id is absent: set `reliability: provisional`.

**Provisional reliability rules — apply at write time:**
- Any log-derived record where provenance (environment, node, trace ID, time window,
  log level) cannot be confirmed → `reliability: provisional`
- Any doc-derived record where the doc may be stale or the scenario is not explicitly
  covered → `reliability: provisional`
- Code-derived and customer_data-derived records where the location was verified
  and reachable → `reliability: high`
- A suspected-area code location (not confirmed reachable) → `reliability: provisional`

---


