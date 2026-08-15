# Stage Protocols

Full execution detail for Stages 0–8 of `manh-issue-triage`.
Loaded by the skill orchestrator at the start of a triage run (or resume).

---

## Controller — State Machine Definition

The controller owns sequencing. The model owns interpretation inside each node.
**The model must never decide what node runs next** — only the controller does,
by reading the node's required output object and applying the exit transition table.

### Node contract schema

Every node is defined by these fixed fields. The model is invoked once per node
with only that node's question and allowed inputs — never with a general
"plan the investigation" prompt.

```
node_id          # identifier matching the stage/step (e.g. N0, N1, N2a, N2b ...)
entry_condition  # when the controller may enter this node
question         # the single focused question the model must answer
allowed_inputs   # which evidence/artifacts the model may consult in this node
required_output  # the typed object the model must return (not prose)
exit_transitions # { output_value → next_node_id } — controller reads, not model
```

### Node registry

| Node | Stage | Question | Required output type |
|---|---|---|---|
| N0 | Stage 0 — Intake | What is the normalized ticket record? | `IntakeRecord` |
| N1 | Stage 1 — Scoping | What is the confirmed functional area, repo set, and completeness score? | `ScopingRecord` |
| N2a | Stage 2 — Documentation | What does the governing documentation say should happen? | `DocRecord` |
| N2b | Stage 2 — Code Localization | What are the candidate code locations reachable from the strongest available anchor? | `LocalizationRecord` (see Change C) |
| N2c | Stage 2 — Reconciliation | How do the reported claim, documented behavior, and actual code behavior compare? | `ReconciliationRecord` |
| N2d | Stage 2 — Hypothesis Sketch | Which cause classes can be confirmed or excluded from current evidence? | `HypothesisSketch` |
| N3 | Stage 3 — Customer Data | Does the customer data validate or refute the leading hypotheses? | `DataValidationRecord` |
| N4 | Stage 4 — Hypothesis Weighing | What is the ranked hypothesis ledger and does a leader clear the exploration-stop threshold? | `HypothesisLedger` |
| N4.5 | Stage 4.5 — Hypothesis FYI | *(operator-node — see socket OS1 below)* | `OperatorSteer` |
| N5 | Stage 5 — Revalidation | Does the leading hypothesis survive stress-testing against docs and code? | `RevalidationRecord` |
| N6 | Stage 6 — Classification | What is the final label, confidence, and Axis-B tag? | `ClassificationRecord` |
| N7 | Stage 7 — Artifact | Is the artifact complete and written to disk? | `ArtifactRecord` |
| N8 | Stage 8 — Handoff | What is the routing instruction and is session state cleaned up? | `HandoffRecord` |

### Transition table

The controller reads the `required_output` from the completed node and applies
these rules to select the next node. The model does not narrate or choose transitions.

| From | Condition on output | To |
|---|---|---|
| N0 | always | N1 |
| N1 | G1a pass | N2a |
| N1 | G1a blocked — operator supplies missing item | re-enter N1 with correction |
| N1 | G1a blocked — operator cannot supply missing item | *(terminal — emit "Cannot start")* |
| N2a | always | N2b |
| N2b | always | N2c |
| N2c | always | N2d |
| N2d | G2b = skip Stage 3 | N4 |
| N2d | G2b = enter Stage 3 | N3 |
| N2d | G2b = proceed on hypotheses (data unavailable) | N4 |
| N3 | Gate G3 pass | N4 |
| N3 | Gate G3 reduced-confidence | N4 |
| N4 | leading hypothesis above exploration-stop threshold | N4.5 |
| N4 | no clear leader + budget remains | N3 *(consume one iteration cycle)* |
| N4 | budget exhausted | N4.5 |
| N4.5 | operator steer applied or auto-proceed | N5 |
| N5 | hypothesis survived | N6 |
| N5 | hypothesis disconfirmed + budget remains | N4 *(consume one iteration cycle)* |
| N5 | budget exhausted | N6 *(at current confidence)* |
| N6 | always | N7 |
| N7 | always | N8 |
| N8 | always | *(terminal)* |

### Termination conditions

A run terminates when N8 completes or when G1a cannot be unblocked after one
operator-input attempt. Every other node must complete before termination —
there is no silent exit.

| Termination trigger | Action |
|---|---|
| N8 completes | Normal terminal — handoff delivered to operator |
| G1a blocked + operator cannot supply missing floor item | Emit "Cannot start" with the specific missing item; do not enter N2a or any later node |

### Forward-looking sockets (leave these open; plugs arrive in later waves)

**Socket OS1 — Operator-node slot:** Node N4.5 is already shaped as an
operator-node. Any future operator seam (Change G) bolts on here as a controller
transition without refactoring the node contract. The slot accepts:
- `executor: operator` — the controller pauses and presents; model is not invoked
- `timeout_action: proceed` — if no operator response, controller auto-advances
- `steer_record` — any operator input is recorded and returned as `OperatorSteer`

**Socket PR0 — Pre-router slot:** a reserved slot ahead of N0 for a future
complexity-triage or fast-path node. When inserted, it runs before N0 and may
route simple tickets to a shortened node sequence. Until it is built, the
controller enters N0 directly. No existing node contract changes when PR0 is
added.

---

## Operating principles (carry through all stages)

- **First pass = localization + optimized IRP (accept the entropy).** On a fresh
  defect, customer data is usually absent. The first run's primary deliverable is
  the minimal, ranked IRP — classification is provisional output. Do not stall or
  guess when data is missing. Evaluate every predicate answerable from docs/code/logs
  alone; **for every predicate left `UNKNOWN`, generate an IRP item** — grouped by
  entity so that multiple predicates answerable from the same config entity or data
  record collapse into one ask. The `blocked_predicates` list links each IRP item to
  all predicates it unblocks; `discriminating_power` is the count of that list. The IRP is the highlighted deliverable when customer data
  is absent — surface it explicitly before the provisional classification.

- **Intelligence proposes, verification disposes (the hunch rule).** A hypothesis
  may be generated from any clue — a weak log signal, a doc sentence, a config
  pattern, a hunch. That generative freedom is preserved and encouraged. But a
  hypothesis in `proposed` status **may not move a classification, enter the defect
  deep-dive, or be cited as the committed label** until it has been converted into
  at least one checkable predicate that has resolved TRUE or FALSE against code or
  logs. A hunch that cannot be expressed as a predicate is retained as a note but
  cannot influence the outcome. A hunch that can be expressed as a predicate but
  cannot be checked with available evidence becomes an IRP item — not a verdict.

- **Reasoning must be reconstructable from the predicate list + evidence records.**
  A reviewer reading only session state Sections 9 and 10 must be able to trace every
  classification decision end-to-end — which predicates were confirmed, by which
  evidence records, and what each result meant for each hypothesis. If the reasoning
  cannot be reconstructed this way, the run is incomplete — do not proceed to N6.
  Two runs on the same ticket and same available evidence must produce diffable
  predicate/evidence sets, not divergent prose.

- **Every fetched fact becomes an evidence record — no exceptions.** Whenever a
  node reads a doc section, traces a code location, reads a log line, or receives
  customer data, it must write a typed `EvidenceRecord` to session state Section 10
  before using the fact in any reasoning. Free-text paragraphs are not evidence —
  only slot-filled records count. A fact not recorded in Section 10 cannot be cited
  in a predicate, a hypothesis, or the output artifact.
  For every record: `locator` must be precise enough to navigate directly to the
  source (file+line for code, doc+section for docs, file+timestamp+trace for logs,
  screen/API for customer_data); `extracted_fact` must state the specific finding —
  never a description of the source. Classify `source_type` by origin not content —
  log files are always `logs` even when they contain customer data values.

- **Every predicate must be written before it can move a classification.** A node
  may not advance a hypothesis status or influence the classification label unless
  the supporting predicate exists in session state Section 9 with a `result` of
  `TRUE` or `FALSE` and at least one `evidence_refs` entry. `UNKNOWN` predicates
  cannot move a classification — they must be resolved or converted to IRP items.

- **Mechanical confidence — derive, never assert.** Hypothesis confidence is computed
  from the count and independence of confirmed predicates and their linked evidence
  records, discounting `provisional` records. The formula applied at N4:
  - Count confirmed predicates (`result: TRUE` or `FALSE`) that support this hypothesis
  - Count their unique `evidence_refs` entries; subtract any marked `provisional`
  - High (≥ 0.7): 2+ confirmed predicates with independent high-reliability evidence
  - Medium (0.4–0.7): 1 confirmed predicate with high-reliability evidence, OR
    2+ confirmed predicates where some evidence is provisional
  - Low (< 0.4): only proposed predicates, or all evidence is provisional
  Replace any narrative "I'm fairly confident" with this derived score.

- **Controller state update — mandatory at every node boundary.** When a node
  completes and its required output object is written to session state, immediately
  update the `controller:` block in the session state YAML header:
  - Set `current-node` to the next node ID (from the exit transition)
  - Set `last-completed-node` to the node just finished
  - Set `last-completed-output-type` to the output type just written
  This ensures a resumed session knows exactly where to re-enter without ambiguity.

- **Evidence before conclusion.** Every material statement links to a concrete source:
  a ticket field, a doc section, a code location, a log line, or a config value.
  Where evidence is missing, list the gap explicitly instead of speculating.
- **Bias to progress.** The default is to keep analyzing. Make labeled assumptions,
  state them, proceed. Stall only when no analyzable path remains.
- **All questions go to the operator.** When the skill needs data it cannot fetch,
  ask the operator — who may answer directly or relay to the customer/reporter.
  If no tool is available for a step, ask the operator to provide the data directly.
- **Accumulate data needs.** Record every gap in the IRR as discovered — consolidated
  into the IRP at the end as one round-trip to the reporter, not many.
- **Observations are evidence; conclusions are hypotheses.** What any source observed
  is evidence. What they concluded or suspected is a hypothesis — treat it with the
  same skepticism. Actively seek disconfirmation of the leading hypothesis.



---

## Iteration budget (shared across N3↔N4 and N5→N4 loops)

Default cycle cap: **5**. The controller increments `iteration-count` in session
state on exactly these two transitions:
- `N4 → no_leader_budget_remains → N3` (returning to gather discriminating data)
- `N5 → disconfirmed_budget_remains → N4` (returning after hypothesis disconfirmation)

**Operator-triggered loops also consume budget:** if an operator steer at N4.5
introduces a new hypothesis that requires a data-gather pass through N3, the
resulting `N4 → N3` transition counts as one cycle. The seam does not bypass the cap.

**Resume sessions reset the counter.** When a run ends as Inconclusive with an IRP
and the operator later resumes with new data, `iteration-count` resets to 0 for the
resumed session — the prior run's cycles are not carried forward.

On reaching the cap:
1. Pause and present to the operator: current leading hypothesis, what remains
   unresolved, what the next cycles would chase.
2. Ask the operator whether to authorize additional cycles (and how many).
3. If authorized: run the operator-specified number of additional cycles, then
   re-prompt at the new limit.
4. If declined or unavailable: proceed with the current best hypothesis. Classify
   if it clears the classification threshold; else emit Inconclusive — iteration cap hit.

Do **not** silently stop at the cap.

---

## Stage 0 — Intake and Normalization
**Node:** N0
**Entry condition:** always (first node in every run; PR0 socket not yet active)
**Question:** What is the normalized ticket record — symptom, environment, error clues, observed facts vs. causal interpretations, and comment/attachment findings?
**Allowed inputs:** raw ticket fields, comments, attachments, linked artifacts
**Required output:** `IntakeRecord` written to session state Section 1 and Section 3
**Exit transition:** always → N1

**Objective:** produce a clean, structured representation of the report and gather
duplicate/known-issue context — without classifying.

**Inputs:** raw ticket, linked artifacts, comments, attachments.

**Actions:**

1. Fetch the ticket and check whether the comments and attachments fields are
   present in the response.

   **Comments:**

   - **Present (even if empty):** accept as-is, no retry needed.
   - **Absent (field not returned at all):** attempt to fetch the full ticket
     including comments via any other available tool (search tool, document
     lookup, or equivalent). Tell the operator which tools were tried and what
     was retrieved. If no tool can supply them, set `comments-unavailable: true`
     in session state — do not ask the operator at this stage. The alert fires
     later at Stage 2 Step 2.6.

   **Comment processing — do before normalization (when comments are retrieved):**
   Filter out noise: status transitions, assignee changes, automated/bot messages,
   and duplicate restatements of the ticket description. Retain only substantive
   comments — operator observations, repro details, workarounds, customer-provided
   data, engineering findings, and resolution attempts.

   For each substantive comment, apply the same **claim vs. reality separation**
   used for all ticket evidence — split content into:
   - **Observations** — what the author directly saw or measured (a value was
     wrong, an error appeared, an action did not happen). Treat these as stated
     facts and record in the assumptions register (Section 3 of session state),
     attributed to the comment author and date.
   - **Interpretations** — what the author concluded, suspected, or inferred
     (this component is responsible, this exception caused it, this step triggered
     it). Flag these explicitly as unverified claims. Record them in the
     assumptions register with an "interpretation — not confirmed" marker. They
     flow into hypothesis candidates and may influence hypothesis ranking, but
     they cannot anchor a conclusion without independent code/doc corroboration.

   **Attachments:**

   - **Present (even if empty):** accept as-is, no retry needed.
   - **Absent (field not returned at all):** attempt to fetch the attachments
     list via any other available tool. Tell the operator which tools were tried
     and what was retrieved. If no tool can supply the list, set
     `attachments-unavailable: true` in session state — do not ask the operator
     at this stage. The alert fires later at Stage 2 Step 2.6.

   **Attachment processing — when attachments are retrieved:**
   For each attachment, attempt to read its content:
   - **Readable** (plain-text logs, CSVs, text files): read and extract relevant
     content; incorporate into the analysis; record findings in session state.
   - **Unreadable** (screenshots, images, videos, ZIPs, emails, PDFs): record
     the filename and type in session state as the `unreadable-attachments` list.
     Do not ask the operator at this stage. The alert fires at Stage 2 Step 2.6.

2. Normalize the ticket into the following internal structure. Extract from ticket
   fields and operator-confirmed comments/attachments:
   - Symptom summary (in business terms)
   - Environment / stack / version / code-drop (good-to-have; note if absent)
   - Business context (functional area signals)
   - Repro steps (retain internally for investigation; omit from artifact if not provided)
   - Expected behavior vs. actual behavior
   - Error codes, stack traces, log clues (include in artifact only if present)
   - Suspected components (from ticket, if any)
   - Business impact — one sentence on operational/business consequence (omit severity label; it is in the artifact YAML header)
   - Workaround (retain internally; omit from artifact)

   **Claim vs. reality separation:** keep reported claim, documented behavior, and
   actual code behavior as separate facts — never merged. Full rule at Step 2.4.

   **Distinguish observed triggers from causal interpretations — apply before writing
   the normalized record:**

   Any input source may contain three types of content alongside observed facts:
   - **Observed facts** — what directly happened: an error appeared, a value was wrong,
     an action did not occur. Keep in the symptom statement.
   - **Observed triggers** — what the user directly did or observed as the initiating
     action: the flow they invoked, the screen they used, the entity they acted on.
     Keep as context — it narrows the execution path legitimately.
   - **Causal interpretations** — what the user suspects, infers, or connects causally
     to another component or flow without having directly observed that connection
     (e.g. "I suspect Booking updates caused it", "the appointment flow may be related").
     Strip from the symptom. Record separately in session state as a **causal candidate**
     with the source noted. It enters the hypothesis ledger in Stage 2 Step 2.5 as an
     unverified claim — not as part of the anchor symptom.

   **The test:** did the user observe this component being involved, or did they reason
   that it might be involved? Observed → keep. Reasoned → causal candidate.

   The normalized symptom must be expressed in observed facts and triggers only. If the
   only description available is framed entirely in causal interpretations with no
   separable observed fact, flag this as a triage-readiness risk and note it in the
   completeness scorecard.

3. Write normalized record and comment/attachment findings to session state
   (Section 1, Section 3, and Section 6 gate log for G0).

**Gate G0:** No early classification exit. Always continue.

**Outputs:** normalized ticket record; comment observations and interpretations (or "none retrieved"); unreadable attachment list (or "none").

---

## Stage 1 — Scoping and Triage-Readiness
**Node:** N1
**Entry condition:** N0 output (`IntakeRecord`) present in session state
**Question:** What is the confirmed functional area, repo set, and completeness score for this ticket — and does it meet the triage-readiness floor?
**Allowed inputs:** session state Section 1, `products/{product}/` artifact directory
**Required output:** `ScopingRecord` written to session state Section 2; completeness scorecard in Section 2
**Exit transitions:** G1a pass → N2a | G1a blocked → await operator input, re-enter N1

**Objective:** confirm the minimum input to begin, classify the ticket into a functional
area by reading product artifacts, confirm with the operator, and judge sufficiency —
proceeding under stated assumptions wherever possible.

**Inputs:** normalized record from Stage 0; `products/{product}/` artifact directory.

**Actions:**

### Step 1.1 — Triage-readiness check (product-agnostic)

Confirm the universal minimum-viable inputs are present:
- An identifiable symptom (expected vs. actual)
- Enough signal to locate a functional area or component
- An anchor transaction or entity (order ID, shipment ID, LPN, etc.)

**Environment and version/code-drop are good-to-have, not required.**
If missing: note to the operator that they are absent and would help, then proceed.

**Gate G1a:** If the universal minimum (symptom + area signal + anchor entity) is
absent, the run cannot start. Name the specific missing item and ask the operator
whether they can obtain it. Missing environment/version alone does **not** block.

### Step 1.2 — Functional area detection

Read `products/{product}/module-index.md` to identify the functional area.
Look for module names, capability keywords, and component references that match the
ticket's symptom, anchor entity, and error clues.

If `module-index.md` is absent or sparse: scan `products/{product}/modules/*.md`
(up to 5 files) to find relevant area.

Identify:
- **Primary area** — the main functional area the ticket belongs to
- **Secondary areas** — any adjacent areas the issue may span (e.g. allocation
  interacting with rating)

If the area is genuinely uncertain after reading module-index: assume the most likely
area, flag it as an assumption (it is load-bearing — every downstream sufficiency
judgment depends on it), and continue.

### Step 1.3 — Load area module and repos

For the identified primary (and secondary) area(s):
- Read the corresponding `products/{product}/modules/{area}.md` for deeper context:
  entities, services, configurations, common failure patterns
- Check `products/{product}/repos.md` to identify which local repos are relevant
  to the area

If `repos.md` is absent: infer relevant repos from the module file content
(service names, component references). Flag the inference as an assumption.

### Step 1.4 — Confirm with operator

**MANDATORY STOP — do not proceed to Step 1.5 until this prompt is emitted and any operator response is recorded. A wrong functional area invalidates every downstream step.**

Present the following to the operator and wait for their confirmation, correction, or explicit "proceed":

```
Based on the ticket and product context, I've identified:

Functional area (primary): {area}
Functional area (secondary): {area, or "none detected"}
Relevant modules: {list of module files read}
Repos identified: {list of repo names and local paths}

Does this look right? Would you like to:
- Correct the functional area?
- Add a secondary area?
- Point to a different or additional repo?
```

Record any corrections or additions in the assumptions register and session state.
Update the repo and module list based on operator input. **Do not enter Stage 2 with an unconfirmed functional area.**

### Step 1.5 — Area-aware completeness scoring

Using the module content loaded for the confirmed area(s), dynamically determine
what inputs are needed to triage this type of issue. Score the ticket's existing
information:

For each meaningful input category inferred from the area module
(e.g. for a rating issue: rate-shop payload, carrier config; for allocation: strategy
config, demand record, inventory snapshot):

Mark as:
- **Present** — available in ticket, comments, or attachments
- **Partial** — partially available (e.g. payload present but truncated)
- **Missing / Blocking** — needed to begin analysis; not present
- **Missing / Good-to-have** — would improve confidence; not strictly required

Record all missing items in the Information Request Register with: what it is, where
to find it (from module knowledge), why it is needed, and whether it is blocking.

**Gate G1b — Area-aware sufficiency:**
- **PASS** — enough exists to begin deep mapping for this area
- **PARTIAL** — items missing but legitimate progress still possible → proceed under
  stated assumptions (default)
- **FAIL** — nothing analyzable remains → name the blocking items and ask operator

The default on PARTIAL is to proceed under stated assumptions. Branch to the operator
only for genuinely decision-blocking gaps (not missing good-to-have items).

**Outputs:** functional area + loaded modules; repo list; completeness scorecard;
assumptions register; operator-confirmed scoping; updated session state.

---

## Stage 2 — Understanding and Mapping

Stage 2 runs as four sequential sub-nodes (N2a → N2b → N2c → N2d). The controller
advances through each automatically on output completion. No sub-node may be skipped.

### Sub-node N2a — Documentation
**Node:** N2a
**Entry condition:** N1 output (`ScopingRecord`) present in session state
**Question:** What does the governing documentation say should happen for this symptom and functional area?
**Allowed inputs:** product module files, functional-architecture.md, Confluence/search tool
**Required output:** `DocRecord` — documented contract, source citation, gap flag if silent or contradictory
**Exit transition:** always → N2b

### Sub-node N2b — Code Localization
**Node:** N2b
**Entry condition:** N2a output (`DocRecord`) present
**Question:** What are the candidate code locations reachable from the strongest available anchor for this symptom — and which are blocked by unknown config/data?
**Allowed inputs:** symptom + error clues from session state Section 1; doc-derived entry points from N2a `DocRecord`; local repo files (Glob/Grep/Read); code search tool; any logs/data present in session state
**Required output:** `LocalizationRecord` — anchors used (with strategy), ordered candidate locations each with `reachable: yes|suspected|unresolved`, path-from-entry, predicate links; UNKNOWN predicates generated for config-gated unresolved branches; evidence records written to Section 10; no classification, no hypothesis commitment at this node
**Exit transition:** always → N2c

### Sub-node N2c — Reconciliation
**Node:** N2c
**Entry condition:** N2b output (`LocalizationRecord`) present
**Question:** How do the reported claim, documented behavior, and actual code behavior compare — and what is the reconciliation verdict?
**Allowed inputs:** session state Section 1 (ticket), N2a `DocRecord`, N2b `LocalizationRecord`
**Required output:** `ReconciliationRecord` — three-column table + reconciliation verdict + documentation-gap flag; written to session state Section 7
**Exit transition:** always → N2d

### Sub-node N2d — Hypothesis Sketch
**Node:** N2d
**Entry condition:** N2c output (`ReconciliationRecord`) present
**Question:** Across the seven cause classes, which can be confirmed, excluded, or remain open from current evidence alone — and is customer data needed to discriminate?
**Allowed inputs:** session state Sections 1, 3, 7, 9, 10; N2a–N2c outputs
**Required output:** `HypothesisSketch` — per-cause-class entry at `lifecycle-status: proposed`, with governing-predicates list (Section 9), supporting/contradicting evidence_ids (from Section 10), mechanically derived confidence; Gate G2b decision recorded in Section 6; all answerable predicates resolved and lifecycle transitions applied before exit
**Exit transitions:** G2b = skip Stage 3 → N4 | G2b = enter Stage 3 → N3 | G2b = proceed on hypotheses → N4

**Objective:** establish three separated facts — the reported claim, the documented
behavior, and the actual code behavior — reconcile them, and form a provisional
hypothesis set rich enough to decide whether customer data is needed.

**Inputs:** normalized record; confirmed functional area + modules; repo list; scorecard.

**Data sources — use in this priority order:**

For product documentation:
1. `products/{product}/modules/{area}.md` — already loaded; read more deeply now
2. `products/{product}/functional-architecture.md` — business capabilities and flows
3. Confluence search (available search/lookup tool) — governing design specs, solution
   docs, knowledge pages for the area. If no tool: ask operator to provide the relevant
   doc or paste the key section.

For code:
1. Local repos identified in Stage 1 — use file search (Glob), content search (Grep),
   and file read (Read) to trace the execution path
2. Bitbucket / code search tool — if local repo is not available or the relevant file
   cannot be found locally. If no tool: ask operator to locate or paste the relevant
   code section.

**Actions:**

### Step 2.1 — Restate the symptom in observed-fact terms

Restate the symptom derived from the normalized record — in observed-fact terms only,
independent of any named component, flow, or causal chain from any input source:
- What specifically did not happen, produced the wrong value, or showed an error —
  for which anchor entity and transaction
- What the expected outcome is in business terms
- What business transaction is involved

Do not carry any causal interpretation into the symptom restatement. Observed triggers
(what the user directly did or invoked) are kept as context. Causal interpretations
(what the user suspects may be connected) are already captured in the causal-candidates
list from Stage 0 — leave them there. This restatement is the independent anchor for
all downstream investigation.

### Step 2.2 — Locate and read governing documentation

Search for the governing documentation for the area and the specific behavior in question:
- Design specs, solution docs, modification specs for the component
- Help docs or knowledge pages describing the expected behavior
- Any known-behavior or exception-handling documentation

Capture: what behavior is documented/specified. Note the source (doc title, section,
Confluence page ID). If documentation is silent or contradictory on the scenario,
flag a documentation/clarity gap and continue.

**Evidence record (write one per doc fact used):** for every documented contract
statement used in analysis, write an `EvidenceRecord` to session state Section 10:
- `source_type: docs`
- `locator`: doc title + section (e.g. "Solution Doc: Rating Engine v3, Section 4.2")
- `extracted_fact`: the specific contract statement — what the doc says should happen
- `reliability: high` if the doc explicitly covers the scenario; `provisional` if
  the doc is silent or the scenario is inferred from adjacent content

### Step 2.3 — Code localization (multi-entry anchoring)

**This node's output is a `LocalizationRecord` — an ordered list of candidate code
locations. The model must not browse files freely. Every location read must be reached
via a structured anchor strategy. The sequence of steps is fixed; interpretation
inside each step is model-driven.**

---

#### Phase 1 — Pick anchors

Use the strongest available strategy. Strategies are fallbacks for each other —
not a fixed pipeline. More than one may have signal; use all that do.

**A1 — Doc-derived entry point**
When N2a (`DocRecord`) names a flow, endpoint, job, or handler: extract the
service/operation/symbol as an anchor. A1 is a bonus — docs may describe behavior
without naming code, or may be absent entirely. If A1 yields nothing, fall through.

**A2 — Feature/symptom name → code search**
Search code for the functionality's names: screen names, transaction types, service
names, domain terms from the symptom. Use Grep/Glob on local repos first; fall back
to Bitbucket code search if repo is not local. Record the search terms used — they
are part of the anchor record.

**A3 — Error/stack signature → code**
When the ticket carries an exception class, stack frame, or error code: locate that
site directly and traverse *backward* toward the trigger. The error site is the
most precise anchor available — prefer A3 over A2 whenever an error signature exists.

**A4 — Log symbol → code** *(Change D handoff)*
When a log line is present: harvest its symbols (method/class names, error strings,
status values, identifiers) and resolve them to code locations. Until Change D lands,
treat log-derived symbols as A2-equivalent anchors and mark the resulting location
`reliability: provisional` in the evidence record. Change D will add full provenance
validation and symbol-harvesting rules on top of this.

**A5 — Data/config artifact → code**
When the only solid signal is a config value, flag name, or data record: locate the
code that reads or writes it. Config artifacts are often the most precise anchor in
Manhattan products — a config field name leads directly to the reader/writer method.

**Record for each anchor selected:**
```
anchor_id:       "ANC1"
strategy:        "{A1|A2|A3|A4|A5}"
signal_used:     "{what in the ticket/docs/logs/data produced this anchor}"
resolved_symbol: "{repo/file/class/method — or 'unresolved' if not found}"
```

**Causal candidate guard:** if the Stage 0 causal-candidates list is non-empty, do
not use those components as anchors. Derive anchors independently from observed facts,
observed triggers, and product module context. Only use a causal candidate as an
anchor if independent derivation reaches the same component. If it does not, the
causal candidate remains a low-priority unverified hypothesis — operator may promote
it at N4.5.

---

#### Phase 2 — Resolve anchors

For each anchor selected in Phase 1:
- Verify the symbol exists in local repo files (Glob → Grep → Read)
- If found: record as resolved; proceed to Phase 3
- If not found locally: attempt Bitbucket code search; if still unresolvable,
  record as `reachable: unresolved` with reason — never fabricate a location

**Grep before reading:** before opening any file, state the question being answered
and run a targeted search for the most specific identifier available. Read only
matching lines and their immediate context (5–10 lines). Escalate to full file read
only if targeted search returns nothing useful — state why before doing so.

---

#### Phase 3 — Traverse, don't browse

From each resolved anchor, follow the call graph toward the symptom:
- Forward from an entry point (A1/A2/A5): follow the execution path toward where
  the symptom manifests
- Backward from an error/log site (A3/A4): follow the call stack toward the trigger

**Read only the reachable path.** At each step, read the next called method/class —
not arbitrary adjacent files. Stop traversal when:
- The fault site is reached (code does the wrong thing at this location), OR
- The path exits the domain of interest (crosses a boundary into infrastructure
  or a third-party library with no Manhattan code), OR
- A config-branch saturation point is reached (see below)

**Config-branch saturation rule (applies when call graph fans out due to unknown
tenant config):** when traversal reaches a branch point controlled by a config
dimension whose value for this tenant is unknown (e.g. `tenantConfig.rateShopStrategy`,
a feature flag, a strategy profile field):
1. Do NOT traverse all branches speculatively — this is browsing, not traversal
2. Record the branch point as a candidate location with `reachable: unresolved`
   and note: *"branch controlled by {config dimension} — value unknown for this tenant"*
3. Generate a predicate immediately:
   - `question`: "What is {tenant}'s {config dimension} value?"
   - `source_required: customer_data`
   - `locator`: config screen / API endpoint that surfaces the value
   - `result: UNKNOWN`
4. Continue traversal only on paths NOT gated by unknown config
   (unconditional code paths that execute regardless of config state)
5. After all unconditional paths are traversed, the `UNKNOWN` predicates become
   IRP items per Change F — the operator fetches the config values and the
   conditional traversal completes on resume

This is the correct application of "reachable path only" — a branch whose execution
condition is unknown is not reachable under stated conditions.

**Layer anchoring — apply before traversal begins:** identify which layer the
failure was observed at. A symptom can manifest at different points in the same chain:
- Wrong value: storage (write path) vs. retrieval/computation vs. rendering
- Action not taken: trigger never fired vs. logic executed but no output vs.
  external call failed
- Wrong selection: wrong candidates in pool vs. incorrect selection logic vs.
  extension override

If the ticket makes the layer clear, proceed. If not, ask the operator one focused
question: "where did you observe the failure?" Accept a partial answer and proceed.
When layers remain ambiguous, track each suspected layer as a separate sub-hypothesis
with its own evidence — do not merge until one is confirmed by evidence.

**Exception context rule:** before using any exception as evidence, confirm it shares
the same thread/transaction as the anchor entity (same trace/correlation ID), or
demonstrably touches the same entity. If a traced code path from the exception to
the observed symptom cannot be established, discard it explicitly with reason stated.
Proximity in time is not causation.

---

#### Phase 4 — Rank candidate locations

Rank the locations found by:
1. **Proximity to symptom** — how directly does this location produce the observed wrong behaviour?
2. **Predicate relevance** — does this location answer an active predicate in Section 9?
3. **Recency of change** — if git history is accessible, recent commits at this location
   raise priority (regression signal)

---

#### Phase 5 — Verify before citing

Any location used in a conclusion, predicate, or the defect deep-dive must be
confirmed to exist and be reachable under the stated trigger conditions.

**Reachability classification (use exactly these three values — spec-defined):**
- `reachable: yes` — location verified to exist and confirmed reachable on the
  execution path for the stated anchor entity and trigger conditions
- `reachable: suspected` — location exists but reachability under stated conditions
  cannot be confirmed from available files; label as "suspected area: {reason}"
  in the evidence record
- `reachable: unresolved` — location cannot be verified from available files, OR
  is gated by an unknown config/flag whose value for this tenant is not available.
  Never cite an `unresolved` location as confirmed — it may generate a predicate
  (if the blocking condition is knowable) or an IRP item

---

#### Graceful degradation — handle each case explicitly

**Code found, no relevant logs:** proceed on code + data. Logs are optional
confirmation. Their absence never blocks traversal and never refutes a hypothesis.

**Log match, no docs, no obvious code (A4 only anchor):** start from the log symbol.
Resolve it to a code location via A4. If code can be located, verify there — the
log lead is confirmed or refuted in code. If code cannot be located, the log lead
stays `reachable: suspected` and becomes an IRP item (request a code pointer or
better-leveled logs) or feeds an Inconclusive stating what would confirm it.

**Docs missing entirely:** skip A1; rely on A2–A5. Missing docs lower confidence
on the intended-contract question (N2a `DocRecord` will be sparse or absent) but
do not stop localization. Proceed with whatever anchors are available.

**Config-heavy ticket with no customer data (many `unresolved` branches):** this
is expected and correct — do not force traversal of unknown branches. The `unresolved`
locations and their blocking predicates become the primary IRP output per Change F.
The first-pass deliverable is the IRP, not a classification.

**Nothing resolves to code from any strategy (all anchors `unresolved`):** go to
the intuition fallback below — do not stall.

---

#### Intuition fallback (when no structured anchor resolves)

The model is explicitly permitted to follow its best hunch from whatever single
artifact is available — one suggestive log line, one odd config value, one doc
sentence — and propose a lead. The hunch rule (Change B) still applies:

- If the hunch can be expressed as a predicate and checked in code → verify it;
  write an evidence record; apply lifecycle transition
- If the hunch can be expressed as a predicate but cannot be checked with available
  evidence → generate the **single most decisive IRP item** (what to fetch to make
  it checkable); emit Inconclusive stating the lead and what would confirm it
- If the hunch cannot be expressed as a predicate → retain as a note in the
  assumptions register; it cannot influence the outcome

The intuition fallback preserves model intelligence on sparse, messy inputs while
preventing unverified intuition from becoming a verdict.

---

#### Output — LocalizationRecord

Write to session state as part of the N2b output. Also write an `EvidenceRecord`
to Section 10 for each location with `reachable: yes` or `reachable: suspected`.

```yaml
localization-record:
  anchors-used:
    - anchor_id: "ANC1"
      strategy: "{A1|A2|A3|A4|A5}"
      signal_used: "{what produced this anchor}"
      resolved_symbol: "{repo/file/class/method}"
  candidate-locations:
    - location_id: "LOC1"
      repo: "{repo name}"
      symbol: "{file/class/method/line range}"
      anchor_strategy: "{A1|A2|A3|A4|A5}"
      path_from_entry: "{A→B→C→this location}"
      reachable: "{yes|suspected|unresolved}"
      reachable_note: "{null | suspected area: {reason} | unresolved: blocked by {config dim} — predicate P{n}}"
      why_relevant: "{why this location matters for the symptom}"
      predicates_it_answers: ["{P1}", "{P2}"]
```

**Storage layer rule:** when traversal identifies a data need that resolves only to
a database table or storage construct, immediately trace upward to find the API
endpoint or service operation that exposes that data. Record the API/UI path in the
evidence record `locator` field alongside or instead of the table reference.
Never record a database table as the sole locator for a customer-data predicate —
it cannot be forwarded to the customer in the IRP.

**Discovery-driven hypothesis entry (apply during code tracing):** if the traversal
reveals evidence suggesting a cause class not currently in the hypothesis ledger —
an unexpected custom extension, an environment-specific branch, a data condition
not previously considered — add a new hypothesis at `lifecycle-status: proposed`
to Section 4 immediately. Write an `EvidenceRecord`, generate a predicate, and
evaluate it before this node exits.

**Acceptance (Step 2.3):** code investigation begins from the strongest available
anchor; follows the reachable call path rather than browsing; degrades gracefully
when any source is missing; flags every cited location `yes`, `suspected`, or
`unresolved`. When nothing resolves to code from any strategy, the run exits through
the intuition fallback to an IRP/Inconclusive — never a guess, never a fabricated
location.

### Step 2.4 — Build the reconciliation table

**Claim vs. reality separation:** what any input source says happened, what the
documentation says should happen, and what the code actually does are three distinct
facts — never merged. Populate each column from its own source only.

Produce the three-column reconciliation:
- **Reported claim** — what any input source says happened (ticket, comments, attachments)
- **Documented behavior** — what the spec/doc says should happen (with source)
- **Actual code behavior** — what the code does at the identified location (with source)

Reconciliation verdict: aligned / claim-contradicts-docs / claim-contradicts-code /
docs-silent / docs-contradictory.

### Step 2.5 — Form provisional hypothesis sketch and generate initial predicates

Across the seven cause classes, note for each whether it can be confirmed or excluded
from code/docs/ticket evidence alone:

1. **Base / product code defect** — code does not implement the documented contract
2. **Configuration** — customer config state explains the behavior
3. **Data** — a specific data condition (record state, UOM, type mismatch) triggers it
4. **Custom / extension code** — user exit, custom DTO, custom router, or mod changes behavior
5. **Environment / platform / integration** — infra, headers, counters, replication, external API
6. **User / SOP error** — operator or end-user action that explains the symptom
7. **As designed** — behavior is correct per contract; expectation is misaligned

**Environment/platform differences are first-class candidate causes — never skip this
class.** Production often behaves differently from lower environments due to clustering,
replication lag, header differences, counter state, infra configuration drift, and
integration partner behavior. Do not discard this class without explicit evidence
against each of its sub-causes. Production-only reproductions, intermittent failures,
and symptoms that differ across tenants or environments are strong signals for this class.

For each: note supporting evidence, contradicting evidence, and confidence so far.
When populating evidence, include comment observations from the assumptions register
(Section 3 of session state) as named evidence items — cite the author, date, and
key statement alongside code and doc evidence. Comment interpretations flagged as
unverified are hypothesis candidates only — do not use them as supporting evidence.
**All hypotheses enter the ledger at `proposed` status.** No hypothesis at N2d
may be assigned `confirmed`, `refuted`, or `under_test` until a predicate has
been evaluated against it. The hunch rule applies from the first entry — even
a hypothesis strongly suggested by a stack trace enters as `proposed` and must
be verified via a predicate before it can drive classification.

Write the sketch to session state Section 4 as the initial hypothesis ledger.

**Predicate generation (run after the hypothesis sketch is written):**

For each active hypothesis, generate the minimum set of predicates that would
confirm or refute it from available evidence. Full model freedom in deciding which
checks matter — the only constraint is that each predicate must be slot-filled:

- `source_required` must be one of: `docs | code | logs | customer_data`
- `locator` must name a specific, checkable location — not a vague area
- `result` must be set to `TRUE`, `FALSE`, or `UNKNOWN` immediately if the evidence
  to answer it already exists in Section 10; otherwise leave `UNKNOWN`
- `moves_on_result` must state what each result means for the hypothesis

Evaluate every predicate against already-available evidence (Section 10) before
moving on. Any predicate answerable from current evidence must be resolved now —
do not defer answerable predicates to a later node.

Write all predicates to session state Section 9. Mark resolved ones with
`stage-resolved: N2d`; leave unresolved ones with `stage-resolved: null`.

**Causal candidates:** for each entry in the causal-candidates list from Stage 0 that
was not reached by independent code tracing in Step 2.3, create a hypothesis ledger
entry with:
- Status: **Unverified-origin**
- Confidence: Low
- Note: "causal interpretation from {source} — not reached by independent execution
  path tracing; operator may promote at Stage 4.5"

These are presented to the operator at Stage 4.5 alongside the evidence-ranked
hypotheses. They are not weighted in the evidence-based ranking until the operator
promotes them and a code/doc trace is performed.

### Step 2.6 — Confirm understanding with operator before proceeding

**MANDATORY STOP — emit this prompt before entering Stage 3 or Stage 4 under any circumstances. Never silently advance to the next stage. Skipping this step risks the entire investigation being built on a wrong frame.**

Present a concise summary of the skill's current understanding to the operator and wait for their response. Auto-proceed only after the full prompt has been emitted — never before.

**Before presenting the understanding summary, emit any applicable alerts from the
list below. Only include alerts that apply — omit any that do not.**

---

*Alert: comments not retrieved* — emit when `comments-unavailable: true` in session state:

> **Comments not retrieved:** I was unable to fetch comments from this ticket after
> trying all available tools. If there are important details in the comments —
> repro notes, engineering findings, customer observations, or workarounds — please
> share them now and I'll incorporate them before proceeding.

---

*Alert: attachments not retrieved* — emit when `attachments-unavailable: true` in session state:

> **Attachments not retrieved:** I was unable to fetch the attachments list from
> this ticket after trying all available tools. If there are any attachments —
> screenshots, log files, error traces, or zip archives — please describe what they
> contain or make the files available locally at a path you can share, and I'll
> incorporate them before proceeding.

---

*Alert: unreadable attachments* — emit when `unreadable-attachments` list is non-empty
in session state. List each entry by filename and type, then:

> **Attachments I could not read:** `{filename1}` (screenshot), `{filename2}` (zip),
> `{filename3}` (email).
>
> For **screenshots or videos**: please describe what they show — any visible values,
> error messages, or UI state.
>
> For **log files or zip archives**: please make them available locally and tell me
> the path, or paste the relevant sections directly.
>
> If any of these contain information critical to the investigation, share it now
> before I proceed.

---

Re-read session state Section 1 (Ticket Summary) now. Do not rely on memory.
Then emit the following re-anchoring check to the operator before the summary:

> **Re-anchoring check:**
> - **Symptom (from session state):** {verbatim observed facts and triggers from
>   Section 1 — not paraphrased}
> - **Causal candidates not pursued:** {list from causal-candidates, or "none"}
> - **Evidence standard:** every hypothesis below has at least one cited source
> - **Disconfirmation:** contradicting evidence actively sought for leading hypothesis
>   — result: {found / not found / inconclusive}
>
> If any line cannot be completed truthfully from session state, correct before proceeding.

> "Before I go deeper, here is my current understanding of the issue:
>
> **What I think the problem is:** {reconciled claim in business terms — what is
> happening vs. what should happen, for the anchor entity}
>
> **Layer anchoring:** {the layer(s) the investigation is focused on, or "not yet
> anchored — investigating all layers" if still ambiguous}
>
> **Hypotheses I'm pursuing:**
> - {H1 — cause class}: {one-line reason why the evidence points here}
> - {H2 — cause class}: {one-line reason why this remains plausible}
>
> **Hypotheses excluded:**
> - {H3 — cause class}: {one-line reason it was ruled out}
>
> Does this match your understanding of the issue? If anything looks wrong —
> the problem description, the layer focus, or the hypothesis direction —
> correct it now before I proceed. Otherwise I'll continue automatically."

Record any operator correction in the assumptions register. If the operator corrects
the issue understanding, revisit the reconciliation table before proceeding. If they
correct a hypothesis, update the ledger. Then continue to Gate G2b.

**Gate G2:** Confirm the claim has been reconciled against both documented behavior
and actual code behavior. If documentation is silent or contradictory, flag the gap
and continue.

**Gate G2b — Customer-data-need test:**

Decide whether Stage 3 is needed:

- **Not needed** — a leading hypothesis already clears the classification threshold
  from code + docs + ticket evidence alone, AND non-defect causes (config/data/user/
  environment) can be excluded without customer specifics.
  → Skip Stage 3. Proceed to Stage 4. Record "valid inputs/config assumed" as a
  stated assumption.

- **Needed and data available** — surviving hypotheses can only be discriminated with
  customer-specific data (config values, transactional records, feature flag state)
  AND the operator confirms they can access or obtain it.
  → Enter Stage 3.

- **Needed but data not available** — hypotheses need customer data to discriminate,
  but the operator cannot obtain it now.
  → Proceed on hypotheses. Explain to the operator which hypotheses remain open and
  at what reduced confidence. Add specific, obtainable data items to the IRR.
  Do not halt.

**Code-evidence-only skip restriction:**

"Not needed" at Gate G2b requires **verified fault manifestation**, not just a
potential fault site. Finding that a method is absent, a branch is unreachable, or a
call is never made in source code is a *potential fault site* — it is not sufficient
alone to skip Stage 3. The potential fault must be confirmed by at least one of:

- A data record showing the field is null/wrong for the anchor entity in the ticket
- A log line (with confirmed provenance) showing the wrong-path branch was taken
- A second corroborating code location traced end-to-end for the anchor transaction

If the only evidence is a code structural observation ("method X is never called,"
"this branch is unreachable," "this setter is missing"), treat Gate G2b as **"Needed
and data available"** — ask the operator for the minimal discriminating data before
applying the skip. Typically: the entity record for the anchor transaction, or a log
snippet confirming the specific code path taken. A one-round-trip data request here
prevents multiple sessions of investigative ambiguity.

**Outputs:** reconciliation table; candidate components and code locations; provisional
hypothesis sketch; documentation-gap flags; Stage 3 entry decision; updated session state.

---

## Stage 3 — Customer Data and Configuration (Conditional)
**Node:** N3
**Entry condition:** G2b = enter Stage 3 OR controller loop-back from N4 (iteration budget permits)
**Question:** Does the customer-specific data (config, transactional records, feature-flag state) validate or refute the leading hypotheses — and what remains unresolved?
**Allowed inputs:** operator-provided data, config screens, transactional records, feature-flag state; session state Section 4 (hypothesis ledger)
**Required output:** `DataValidationRecord` — per-hypothesis validation result, updated confidence scores, new IRR items for unresolved gaps; written to session state Sections 4 and 5
**Exit transitions:** Gate G3 pass or reduced-confidence → N4

**Objective:** use customer-specific data to validate the leading hypothesis or
discriminate between tied ones. This stage is **skipped whenever Gate G2b finds it
unnecessary**. It is never mandatory.

**Inputs:** reconciliation table; provisional hypotheses; area module content.

**Actions:**

### Step 3.1 — Identify the minimum discriminating data

Identify the minimal set of customer data that would discriminate between the surviving
hypotheses. Examples:
- Feature-flag state for the tenant
- Profile or strategy configuration values
- The specific transactional records (order, shipment, LPN) for the anchor entity
- Recent config edits or deployment events
- Environment or stack details

Ask for the data from the operator. Be specific — name the entity, ID, field, or
screen — not a generic "send logs."

**Feature flag rule — verify, never assume.** Feature flags in Manhattan products are
platform-controlled and can differ per tenant, environment, and release. Never assume
a flag is on or off based on general knowledge. Always request the actual flag state
for the specific tenant and environment in scope. If the flag state cannot be obtained,
record this as a load-bearing assumption ("proceeding assuming flag {X} is off"),
lower the confidence of any hypothesis affected by it, and add it to the IRR as a
Blocking item.

**Read-only environment access.** Where feasible, request read-only access to the
customer's environment (system logs, config screens, transaction records) for the
operator or a designated support engineer. Resolution speed depends directly on the
ability to observe actual system behavior and log output. Record any outstanding
environment access needs in the IRR.

### Step 3.2 — Validate or proceed

**If data is available:**
- Gather the data (operator fetches directly or relays from customer)
- Write an `EvidenceRecord` to session state Section 10 for each datum received:
  - `source_type: customer_data`
  - `locator`: config screen + field path, or entity type + ID + field name
  - `extracted_fact`: the actual value observed
  - `reliability: high` (operator-confirmed direct observation)
- For each open predicate in Section 9 whose `source_required` is `customer_data`,
  evaluate it against the new evidence records and set `result: TRUE | FALSE`
  and `evidence_refs` immediately. Mark `stage-resolved: N3`.
- Update hypothesis ledger confidence using the mechanical confidence rule
  (count confirmed predicates + high-reliability evidence refs)
- Confirm or exclude cause classes based on resolved predicates only

**Discovery-driven hypothesis entry (apply when customer data is received):** if
a customer data record reveals a condition — a config value, a flag state, a data
shape — that implies a cause class not currently in the ledger, add a new hypothesis
at `lifecycle-status: proposed` to Section 4, write an `EvidenceRecord` for it,
and generate and evaluate a predicate for it before this node exits.

**If data is not available:**
- Do not stall. Continue on hypotheses.
- Predicates that needed this data remain `result: UNKNOWN`
- Lower the confidence of hypotheses whose only supporting predicates are `UNKNOWN`
- Record the load-bearing assumption ("proceeding assuming feature flag X is off")
- Add the outstanding data to the IRR with where to get it and why it matters

### Step 3.3 — Decide whether to loop

If a remaining hypothesis tie depends on a specific datum that the operator can still
fetch quickly: record the need, fetch just that datum, and re-weigh.

This stage iterates with Stage 4 within the shared cycle budget. Each return to Stage 4
from Stage 3 consumes one iteration cycle.

**Gate G3:** entered only when Gate G2b requires it. With data: validate and proceed
to Stage 4. Without data: proceed at reduced confidence under stated assumptions.

**Outputs:** customer data/config validation results (if gathered); reduced-confidence
flags and stated assumptions (if not); updated hypothesis ledger; updated IRR.

---

## Stage 4 — Hypothesis Enumeration and Weighing
**Node:** N4
**Entry condition:** N2d output (`HypothesisSketch`) present; OR N3 output (`DataValidationRecord`) present; OR loop-back from N5 with disconfirmation note
**Question:** What is the fully scored and ranked hypothesis ledger — and does the leading hypothesis clear the exploration-stop threshold?
**Allowed inputs:** session state Sections 1, 3, 4, 5, 7, 9, 10; all prior node outputs
**Required output:** `HypothesisLedger` — ranked entries with mechanically derived confidence scores (predicate count × evidence reliability), evidence_id citations from Section 10, predicate status from Section 9; Gate G4 decision; iteration-count incremented if loop-back; written to session state Section 4 and Section 6 gate log
**Exit transitions:** leader above exploration-stop threshold → N4.5 | no clear leader + budget remains → N3 | budget exhausted → N4.5

**Objective:** enumerate every credible cause and weigh them. Do not collapse to one
cause prematurely.

**Inputs:** reconciliation table; provisional hypothesis sketch; customer data/config
validation from Stage 3 (if run).

**Actions:**

### Step 4.1 — Finalize hypothesis ledger

For each of the seven cause classes, produce a full hypothesis entry:
- **Cause class:** which class (base code / config / data / custom-extension /
  environment-platform / user-error / as-designed)
- **Statement:** what this hypothesis claims is the root cause
- **Supporting evidence:** cite `evidence_id` values from Section 10 — not prose
- **Contradicting evidence:** cite `evidence_id` values from Section 10 — not prose
- **Reasoning:** why this hypothesis could explain the observed symptom
- **Confidence:** derived mechanically — do not assert; compute per the rule below

**Lifecycle transitions at N4 (apply after predicate evaluation):**
- Any hypothesis with at least one governing predicate now being evaluated →
  transition from `proposed` to `under_test`
- Any hypothesis whose governing predicate resolved `TRUE` with high-reliability
  evidence → transition to `confirmed`
- Any hypothesis whose governing predicate resolved `FALSE` eliminating the cause
  class → transition to `refuted`
- Any hypothesis all of whose governing predicates remain `UNKNOWN` → stays
  `under_test` (not yet `unknown` — that status is set at N5 end only)

**Hard rule at N4:** do not rank or score a `proposed` hypothesis against a
`confirmed` one. A `proposed` hypothesis has no confirmed predicate and therefore
a mechanically derived confidence of Low (< 0.4) by definition — it cannot clear
the exploration-stop threshold regardless of how strongly the model believes it.

**Discovery-driven hypothesis entry at N4:** if the scoring and ranking process
reveals an evidence pattern that points to a cause class not yet in the ledger
(e.g. two evidence records that together suggest an environment/platform cause
that was not individually obvious), add a new hypothesis at `lifecycle-status:
proposed`, generate a predicate, and evaluate it before Gate G4 fires.

**Additional predicate generation at N4:** if new evidence has been added since N2d
(e.g. from N3 customer data), generate any additional predicates the new evidence
enables. Evaluate them immediately against Section 10 evidence records. Write to
Section 9 with `stage-generated: N4`.

**Environment / platform / integration is a required cause class — never skip it.**
Production-only reproductions, intermittent failures, and symptoms that differ across
tenants or environments are strong signals for this class. Candidate causes include:
clustering/replication lag, load-balancer header differences, counter or sequence state,
infra configuration drift between environments, external API or integration partner
behavior, and certificate/connectivity issues. Do not discard this class without explicit evidence against each of its sub-causes.

Cause classes that are clearly excluded by evidence: mark as discarded with the
reason. Do not delete — retain in ledger.

### Step 4.2 — Score and rank using mechanical confidence

Apply the mechanical confidence rule from Operating Principles to each active hypothesis:
1. Count predicates in Section 9 with `result: TRUE` or `FALSE` that list this
   hypothesis in `tests_hypotheses`
2. From their `evidence_refs`, count unique evidence records in Section 10;
   subtract any with `reliability: provisional`
3. Assign: High (≥ 0.7) / Medium (0.4–0.7) / Low (< 0.4) per the rule thresholds
4. Record the derived `confidence-score` in the hypothesis ledger — do not override
   with a narrative judgement

Apply the three thresholds from CLASSIFICATION-LOGIC.md:
- Is the leading hypothesis above the **exploration-stop threshold**? (clearly ahead
  of runner-up) → proceed to Gate G4
- Is any hypothesis above the **classification threshold** already? (High confidence
  AND meaningfully ahead) → note it; still proceed through Stage 5

### Step 4.3 — Decide on additional evidence

If no hypothesis clears the exploration-stop threshold AND iteration budget remains:
- Identify a specific datum that would break the tie
- Consider a low-risk diagnostic experiment (e.g. "check whether config X changes
  the behavior in a non-prod copy")
- Loop back to Stage 3 for that specific datum, consuming one iteration cycle

**Gate G4:**
- **Leading hypothesis above threshold** → proceed to Stage 4.5 then Stage 5
- **No clear leader + budget remains** → loop to Stage 3 for specific deciding datum
- **Budget exhausted** → proceed with best-supported hypothesis to Stage 4.5/5

**Outputs:** ranked hypothesis ledger with confidence, evidence, reasoning per item;
updated session state.

---

## Stage 4.5 — Hypothesis-Review FYI to Operator (Non-Blocking)
**Node:** N4.5
**Socket:** OS1 (operator-node slot)
**Entry condition:** N4 output (`HypothesisLedger`) present
**Question:** *(operator-facing, not model-answering)* Does the operator wish to discard, reprioritize, add a hypothesis, supply a fact, or promote a causal candidate — before revalidation begins?
**Executor:** operator (controller presents the FYI; model is not invoked for the question itself)
**Timeout action:** proceed — if no operator response, controller auto-advances to N5
**Required output:** `OperatorSteer` — steer type, steer content, timestamp, applied-flag; written to session state Section 6 gate log (G4.5); null steer recorded if operator silent
**Exit transition:** operator steer applied (or null) → N5

**Objective:** before spending the revalidation budget, tell the operator which
hypotheses the skill is targeting and why — and give them a window to apply domain
knowledge — without stalling the run.

**Inputs:** ranked hypothesis ledger from Stage 4.

**Actions:**

### Step 4.5.1 — Present the FYI

Re-read session state Sections 1 (Ticket Summary), 4 (Hypothesis Ledger), and 7
(Reconciliation Table) now. Do not rely on memory. Then emit the following
re-anchoring check to the operator before the hypothesis FYI:

> **Re-anchoring check:**
> - **Symptom (from session state):** {verbatim observed facts and triggers from
>   Section 1 — not paraphrased}
> - **Causal candidates not pursued:** {list from causal-candidates, or "none"}
> - **Reconciliation verdict:** {from Section 7 — aligned / claim-contradicts-docs /
>   claim-contradicts-code / docs-silent / docs-contradictory}
> - **Evidence standard:** every Active/Leading hypothesis has both supporting AND
>   contradicting evidence listed — {confirmed / gaps: {list any hypothesis missing
>   contradicting evidence}}
> - **Environment/platform hypothesis:** {Active / Discarded — reason: {reason} /
>   Not yet evaluated — must be evaluated before proceeding to revalidation}
>
> If any line above cannot be completed truthfully from session state, correct it
> before proceeding.

Present a concise, non-blocking FYI to the operator:

```
Based on the evidence so far, I'm targeting the following hypotheses for revalidation:

Leading: {H1} — {cause class}: {one-line statement} [{confidence}]
  Reasoning: {why the evidence points here}

Runner-up: {H2} — {cause class}: {one-line statement} [{confidence}]
  Reasoning: {why this remains plausible}

Discarded: {H3} ({reason}), {H4} ({reason})

{If causal candidates exist in the ledger — include this block, otherwise omit:}
Not pursued (causal interpretations from input — not reached by independent tracing):
  {CC1} — {component/flow name} (from {source}): not on independently derived
    execution path. Include if you believe it is relevant.

I'm proceeding to revalidation unless you'd like to:
- Discard a hypothesis (with reason)
- Reprioritize or focus on specific ones
- Add a hypothesis I may have missed
- Supply a fact that collapses a branch
- Promote a "not pursued" flow for investigation

If no response, I'll proceed automatically.
```

### Step 4.5.2 — Auto-proceed

Proceed to Stage 5 on the skill's own reasoning without waiting. If the operator
responds, apply their steer before revalidation begins.

**When this gate fires:**
- Once on the first weighing pass
- Again (as a brief delta FYI) only if the hypothesis set has materially changed:
  a hypothesis added or discarded, or a new leader emerging

**Applying operator steer:**
- **Discard:** set `lifecycle-status: discarded-by-operator`; record reason. If
  the hypothesis carries supporting evidence that contradicts the discard, note the
  tension — do not delete the evidence or the hypothesis entry.
- **Reprioritize / focus:** reorder the ledger accordingly; record the steer.
  Lifecycle status is not changed by reprioritization alone.
- **Add:** enter the new hypothesis at `lifecycle-status: proposed` with
  `operator-steer: added by operator`; it must follow the full hunch rule —
  a predicate must be generated and evaluated before it can be `confirmed`.
  If the operator supplies a fact alongside the hypothesis, write an evidence
  record (Section 10) and evaluate whether it immediately resolves a predicate.
- **Supply a fact:** write an `EvidenceRecord` to Section 10; evaluate all open
  predicates in Section 9 against it; apply lifecycle transitions as warranted.
- **Promote a causal candidate:** set `lifecycle-status: proposed` (not Active —
  it has no confirmed predicate yet); record "promoted by operator"; generate a
  predicate for it and evaluate via code/doc trace before N5; apply lifecycle
  transitions based on what the trace finds.
- All steers are recorded explicitly — never applied silently.

**Gate G4.5:** auto-proceed (non-blocking). Apply and record any operator steer.

**Outputs:** FYI record; operator steer applied and logged; updated hypothesis ledger.

---

## Stage 5 — Revalidation Against Documentation and Code
**Node:** N5
**Entry condition:** N4.5 output (`OperatorSteer`) present (null steer is valid)
**Question:** Does the leading hypothesis survive stress-testing — does the code path actually produce the observed symptom under the stated trigger conditions?
**Allowed inputs:** leading hypothesis from session state Section 4; N2a `DocRecord`; N2b `LocalizationRecord`; local repo files for re-tracing
**Required output:** `RevalidationRecord` — verified code locations with reachability status, contract-vs-actual comparison, hypothesis survival verdict, disconfirmation note if failed; written to session state Section 8
**Exit transitions:** hypothesis survived → N6 | disconfirmed + budget remains → N4 (consume one iteration cycle) | budget exhausted → N6 (at current confidence)

**Objective:** stress-test the leading hypothesis against docs and code before
committing. Confirm the code path actually produces the observed symptom under the
stated conditions.

**Inputs:** leading hypothesis; reconciliation table; code locations from Stage 2.

**Actions:**

### Step 5.1 — Re-read governing documentation for the hypothesis

Re-open the governing doc specifically for the leading hypothesis:
- Does the documented contract support a "defect" vs "as designed" conclusion?
- Is there any documented exception or edge-case behavior that explains the symptom
  as intended?
- Has there been a recent base validation or contract change that could explain
  a previously-working custom flow now breaking? (This is a known pattern.)

### Step 5.2 — Re-trace code for the leading hypothesis

Re-trace the implicated code path specifically for the leading hypothesis:
- Confirm the code path produces the observed symptom under the stated trigger
  conditions and data shape
- Confirm the fault site is actually reachable given the anchor entity and repro steps
- Check for recent changes at the fault site (if git history is accessible in local repo)

**Anti-hallucination rule (critical):** confirm each cited code location actually
exists and is reachable on the execution path for the stated trigger conditions.
- If the exact location was verified: mark as verified in the revalidation record
- If a location cannot be confirmed from available files: label it "suspected area:
  {reason}" — do not hand it to the fix agent as confirmed

### Step 5.3 — Produce revalidation record

Before finalizing the revalidation verdict, re-apply the log evidence check: if any
conclusion rests on a log line, confirm provenance (environment, node, trace ID, time
window, log level enabled). A log-based conclusion with unconfirmed provenance must be
marked provisional — it cannot be the sole basis for committing a classification.

**Evidence records for revalidation:** write a new `EvidenceRecord` to Section 10
for each code location re-traced in Step 5.2 (even if previously traced in N2b) —
re-tracing constitutes independent confirmation and should be recorded separately.
Set `reliability: high` only if the location is confirmed reachable under the stated
trigger conditions in this pass.

**Predicate resolution at N5:** for any predicate in Section 9 still marked
`result: UNKNOWN` that can now be answered from the revalidation trace, resolve it
now — set `result`, populate `evidence_refs`, and mark `stage-resolved: N5`.
A hypothesis may only be committed as surviving if its core predicates are `TRUE`
(not `UNKNOWN`). If core predicates remain `UNKNOWN` after revalidation, the
hypothesis has not survived — it becomes Inconclusive (insufficient information).

**Discovery-driven hypothesis entry at N5:** if the revalidation trace encounters
an execution path or fault condition that implies a cause class not in the ledger,
add it at `lifecycle-status: proposed`, generate a predicate, and evaluate it
within this node. If it resolves in N5, apply lifecycle transitions. If it remains
`UNKNOWN`, it becomes an IRP item — it does not block the N5 exit transition.

**Lifecycle finalization at N5 end (before exit transition):**
- Leading hypothesis with at least one core predicate `TRUE` → `confirmed`
- Leading hypothesis with all core predicates `UNKNOWN` after revalidation → `unknown`
  (treated as `proposed` for classification — cannot drive a label)
- Any hypothesis disconfirmed by a `FALSE` predicate in revalidation → `refuted`
- A hypothesis that is `unknown` at N5 exit must NOT be handed to Stage 6 as the
  committed label — route to Inconclusive (insufficient information) instead

Record in Section 8:
- Code locations verified (file/function/line range) with verified: true/false
- What the code does at the location vs. what the documented contract requires
- Whether the hypothesis survived or was disconfirmed
- If disconfirmed: the specific disconfirming evidence and which predicate it falsified
- Any log-derived conclusions marked provisional with provenance gap noted
- Final predicate resolution summary: how many predicates confirmed, how many remain UNKNOWN

Write to session state (Section 8 — revalidation record).

**Gate G5:**
- **Hypothesis survives** → advance to Stage 6 (Classification)
- **Hypothesis fails + iteration budget remains** → return to Stage 4 with the
  disconfirming evidence recorded (consume one iteration cycle)
- **Budget exhausted** → stop looping. Proceed with the best-supported hypothesis
  at its current confidence. Classify if above the classification threshold;
  else emit Inconclusive — iteration cap hit.

**Outputs:** validated root-cause statement with precise code/doc references, or a
disconfirmation note, or a cap-reached note; updated session state.

---

## Stage 6 — Classification Decision
**Node:** N6
**Entry condition:** N5 output (`RevalidationRecord`) present
**Question:** What is the single final classification label, confidence level, and Axis-B resolution-path tag — derived from the two-axis model and confirmed evidence?
**Allowed inputs:** session state Sections 4, 6, 7, 8; `CLASSIFICATION-LOGIC.md` (load now if not already loaded)
**Required output:** `ClassificationRecord` — label, confidence, Axis-A verdict, Axis-B tag, evidence references, reason statement; written to session state Section 6 gate log
**Exit transition:** always → N7

**Objective:** assign exactly one final label using the two-axis model and logic in
`CLASSIFICATION-LOGIC.md`.

**Inputs:** validated root-cause statement; hypothesis ledger; customer version/code-drop
(if available).

**Actions:**

1. Load `CLASSIFICATION-LOGIC.md` now if not already loaded.
2. Score on Axis A (cause) and Axis B (resolution path).
3. Apply the config decision sub-tree (Section 5 of CLASSIFICATION-LOGIC.md) if
   the symptom is config-adjacent.
4. Assign the final label. If the classification threshold is not met by any
   hypothesis: emit the appropriate Inconclusive variant.
5. Attach the Axis-B resolution-path tag regardless of whether the primary label
   is debatable.

**Outputs:** classification label + Axis-B tag + structured reason + confidence +
evidence references; updated session state Section 6 gate log with Stage 6 exit check result and classification record.

**Stage 6 entry check (hunch rule gate — run before any classification work):**
Inspect the `lifecycle-status` of the hypothesis being considered for the label:
- `confirmed` → may proceed to classification
- `refuted` → may not be the committed label (may explain a non-defect path)
- `proposed` → **hard block** — do not classify; the hunch rule was violated if
  this hypothesis reached N6 as `proposed`; reclassify as Inconclusive and flag
  as a run integrity issue
- `unknown` → **hard block** — cannot be the committed label; emit Inconclusive
  (insufficient information) with an IRP covering what would move it to `confirmed`
- `discarded` / `discarded-by-operator` → may not be the committed label

**Stage 6 exit check — before proceeding to Stage 7:**
- The committed hypothesis has `lifecycle-status: confirmed`
- The classification label has at least one cited evidence item that directly
  supports it — not just the absence of evidence for competing labels
- The committed label is traceable to at least one confirmed predicate (`result: TRUE`
  or `FALSE`) in session state Section 9, with at least one `evidence_refs` entry
  in Section 10 — no label is justified by narrative alone
- Confidence score was derived mechanically (predicate count × evidence reliability),
  not asserted — verify Section 9 predicate counts match the score
- Any load-bearing assumption the classification rests on is documented
- The Axis-B resolution path tag is attached

If any item fails, correct before proceeding — lower confidence, add missing
predicate or evidence entries, or reclassify as Inconclusive rather than carrying
an unsupported label into the artifact.

---

## Stage 7 — Explanation Artifact Generation
**Node:** N7
**Entry condition:** N6 output (`ClassificationRecord`) present
**Question:** Is the artifact complete — all 8 sections populated from session state — and written to disk?
**Allowed inputs:** all session state sections; `OUTPUT-ARTIFACT-TEMPLATE.md` (re-load from disk); `ClassificationRecord` from N6
**Required output:** `ArtifactRecord` — confirmation artifact written to `products/{product}/triage/{ticket-id}-triage.md`; section completeness check passed; closing Quick Summary rendered to terminal
**Exit transition:** always → N8

*(Change G — G3 inline-resolve seam — plugs into this node before IRP packaging. Until Change G lands, all UNKNOWN items go directly to the ranked IRP.)*

**Objective:** produce the operator-facing triage artifact.

**Inputs:** all session state; classification result; reconciliation table; hypothesis
ledger; IRR.

**Compact-resilience pre-flight (run before any other action in this stage):**

Context compaction may have evicted files loaded in earlier stages. Before doing
anything else, re-load the following from disk — do not rely on in-memory copies:

1. Read `references/OUTPUT-ARTIFACT-TEMPLATE.md` from disk now.
2. Read `products/{product}/triage/.session-{session-key}.md` from disk now.

If either file cannot be read, stop and tell the operator:
> "I need to re-load the artifact template / session state before generating the
> report. Please confirm the paths are accessible and I'll continue."

**Actions:**

1. With the template and session state both freshly read from disk, confirm
   `OUTPUT-ARTIFACT-TEMPLATE.md` is loaded (all 8 section headers present).
   **Copy the template structure verbatim. Fill each placeholder slot from
   session state. Do not rewrite, reformat, or summarize sections — slot-fill
   only. Any section not filled from session state must be explicitly marked
   "Not applicable" — never omitted.**
2. Fill every section of the template using the session state file as the
   primary data source — read each section of `.session-{session-key}.md`
   in turn and map it to the corresponding artifact section:
   - Section 1 (Ticket Summary) → artifact Section 1
   - Section 2 (Scoping) → artifact Sections 1 (environment/area) and 5
   - Section 3 (Assumptions Register) → artifact Section 5
   - Section 4 (Hypothesis Ledger) → artifact Section 3
   - Section 5 (IRR) → artifact Section 7
   - Section 6 (Gate Decisions Log) → artifact Sections 2 and 5
   - Section 7 (Reconciliation Table) → artifact Section 3 and Section 2
   - Section 8 (Revalidation Record) → artifact Section 4 (if defect)
3. For **Existing Defect only**: populate Section 4 of the template (Defect Deep-Dive).
    Use only code locations marked as verified in Stage 5. Label unverified locations
    as "suspected area" — never present them as confirmed to the fix skill.
4. **Generate the ranked Information Request Package (Change F).**

   **First-pass framing:** on a fresh defect where customer data was absent, the
   first run typically cannot finish classifying. In this case the IRP is the
   primary deliverable — explicitly frame it as "here is exactly what to fetch
   to finish." The provisional classification (or Inconclusive variant) is
   secondary. Do not hide the IRP behind the classification — surface it first.

   **Step 4a — Collect all UNKNOWN predicates from Section 9.**
   Every predicate with `result: UNKNOWN` that was not resolved during the run
   must produce an IRR entry. Verify that every such predicate already has a
   corresponding IR entry in Section 5 (they should have been written when first
   generated). If any are missing, write them now before proceeding.

   **Step 4b — Group predicates by entity before computing discriminating power.**

   This is the consolidation step — do not skip it.

   For each UNKNOWN predicate in Section 9, identify the **entity-level locator**
   that answers it: the config entity, data record type, API endpoint, or screen
   that returns the value. This is coarser than the predicate's own `locator`
   (which may name a specific field) — it is the container that holds the field.

   Examples:
   - Predicates about `rateShopStrategy`, `carrierPriority`, `zoneMatrixId` on
     `CarrierProfile` → entity-level locator: `CarrierProfile config entity`
   - Predicates about `defaultCarrier`, `fallbackEnabled` on `TenantRatingConfig`
     → entity-level locator: `TenantRatingConfig config entity`
   - A predicate about a specific order record → entity-level locator: `Order {id}`

   **Grouping rule:** predicates that share the same entity-level locator collapse
   into one IR item. The collapsed IR item:
   - `what` = the entity name, not individual fields (e.g. "CarrierProfile config
     for tenant ACME" — not "rateShopStrategy field")
   - `where_to_source` = the single API call or screen that returns the whole
     entity (e.g. "GET /api/carrier-profiles/{tenantId}" or "Carrier Management
     screen > Tenant Config > CarrierProfile")
   - `blocked_predicates` = list of all predicate_ids the entity answers
   - `discriminating_power` = count of all predicates in the group (sum, not max)

   **Result:** the IRP contains one IR item per distinct entity, not one per
   predicate field. A 10-predicate UNKNOWN set across 2 config entities produces
   2 IR items, not 10.

   **Step 4b.5 — Compute discriminating power for each grouped IR item.**
   After grouping, for each IR item: `discriminating_power` = count of predicates
   in `blocked_predicates`. Update the IRR entry. One grouped item that unblocks
   seven predicates outranks seven items that each unblock one.

   **Step 4c — Apply the minimum-discriminating-set + stop rule.**
   - Select the **smallest set of IR items** that separates the leading hypotheses
     from runner-up hypotheses — the minimum-discriminating set
   - Within the minimum set, prefer the single most decisive, lowest-effort item
     when several would do the same job (operator-fetch over relay-to-customer;
     config screen check over log capture)
   - **Stop rule:** once the minimum-discriminating set is identified, hold all
     remaining IR items. Do not add more items until the minimum set is answered.
     Do not gather for completeness.
   - Mark IR items outside the minimum-discriminating set as
     `priority: Good-to-have` or `priority: Optional-not-requested` based on
     their decision impact. Items with negligible impact get
     `Optional-not-requested` and are NOT included in the request to the reporter.

   **Step 4d — Rank the IRP.**
   Within the minimum-discriminating set, rank by:
   1. `discriminating_power` descending — highest-impact item first
   2. `obtainability: operator-fetch` before `relay-to-customer` at equal power
   3. Config/flag checks before log-capture requests at equal power and obtainability

   **Step 4e — Populate artifact Section 7 from the ranked IRR.**
   Section 7 has two parts:

   **7.1 Analysis Reference** — operator-facing table ranked by discriminating power.

   **Data retrieval rule — apply to every "Where to get it" entry:**
   Always provide every retrieval mechanism you can identify: the API endpoint or
   operation, the UI screen and navigation path, and the log source with filter.
   Do not omit one because another is available — operators have different levels
   of access. Never reference internal database tables, SQL queries, or storage
   layer constructs. If the only known source is a database table, describe the
   API or business operation that surfaces the same data instead.

   Apply the following specificity rules when writing "Where to get it":
   - **For logs:** specify (a) the exact component service name (e.g.
     component-routing, component-shipment), (b) the specific class and method
     identified during code tracing where the exception or event occurs, and
     (c) the log level to enable — default to DEBUG unless the traced log
     statement is at a different level. Generic entries like "server logs" or
     "application logs" are not acceptable.
   - **For API calls:** specify the exact endpoint, HTTP method, and request
     body. Generic entries like "query the API" are not acceptable.
   - **For UI navigation:** specify the exact screen path and which fields to
     capture. Generic entries like "check in ULC" are not acceptable.

   **7.2 Request to Reporter** — polite, plain-language block for direct copy-paste
   into the JIRA comment. Generation rules:
   - **Derived from 7.1 only** — retrieval paths in 7.2 come exclusively from the
     "Where to get it" column of 7.1. Never independently invent paths not already
     stated in 7.1. If 7.1 says API only, 7.2 offers API only.
   - **Fit-for-purpose paths only (apply in 7.1 first, carry through to 7.2):**
     before stating a retrieval path, confirm it can actually answer the specific
     question being asked. A path that surfaces the symptom (e.g. a UI screen
     showing the wrong value) cannot confirm the underlying cause — only sources
     at the layer where the cause originates can do that.
   - **Include only Blocking and Good-to-have items** — do not include
     `Optional-not-requested` items in the request to the reporter
   - Blocking items under "Required"; Good-to-have under "If you are also able
     to provide the following, it would help us further"
   - Consolidate: if one action yields multiple needed items, ask once
   - Ask for data only — no rationale, no hypothesis references, no internal context
   - Use polite language throughout — this gets posted directly on the ticket
   - For API calls: include exact endpoint and request body
   - For UI navigation: include exact screen path (only when UI is not the suspect layer)
   - For logs: include the component service name and log level to enable
     (default DEBUG); class/method is for 7.1 only — omit from 7.2
   - Open with "Could you please help us..." and close with "Thank you for your help."

   **Always populate Section 7 when any IRR items remain open** — including runs
   that reached a successful classification but still have open good-to-have items.
   The IRP is not only for Inconclusive runs. State "No further data required" only
   when every IRR item was resolved during the run.

   - **For any Inconclusive outcome:** the IRP must cover all active competing
     hypotheses — not just the leading one. For each active hypothesis, include
     its minimum-discriminating IR items grouped by hypothesis so the operator
     can see which data resolves which branch.
   - **For classified outcomes where confidence is not High, or load-bearing
     assumptions remain unvalidated:** include the top 1–2 Good-to-have IR items
     that would most raise confidence — not exhaustively.

5. **Section completeness check — run before writing to disk:**
   Verify the filled artifact contains all 8 section headers and that the
   following sections are non-empty:
   - `## Quick Summary` — all 5 fields populated (Issue, Verdict, Confidence, Next action, Outstanding data)
   - `## 1. Issue Summary`
   - `## 2. Decision` — classification, confidence, reason all present
   - `## 3. Hypotheses Considered` — at least one hypothesis block present
   - `## 4. Defect Deep-Dive` — either populated (defect) or marked "Not applicable"
   - `## 5. Completeness and Assumptions`
   - `## 6. Next Steps`
   - `## 7. Information Request Package` — 7.1 table present; 7.2 Request to Reporter block present with at least one item (or explicitly states "No outstanding information requests")

   If any required section is empty or missing: fill it from the session state
   file before writing. Do not write a partial artifact.

   **Section 6 → Section 7 cross-check (run for every outcome):**
   For every data item referenced in Section 6 Next Steps, confirm it exists as
   a named entry in Section 7. If any Section 6 item is absent from Section 7,
   add it there before writing — as Blocking if Section 6 treats it as an
   immediate action, Good-to-have otherwise. Section 7 may always contain more
   items than Section 6; that is expected and correct. The check is one-directional:
   Section 6 ⊆ Section 7, never the reverse.

6. Write the completed artifact to:
   `products/{product}/triage/{ticket-id}-triage.md`
7. **Render the closing Quick Summary to the terminal immediately after writing the file.**
   This is the sole in-chat output for triage completion — mandatory, do not skip:

   ```
   ── Quick Summary ─────────────────────────────────────────
   Triage complete: {ticket-id}
   Issue:            {one sentence — what is wrong, in business terms}
   Verdict:          {classification label} — {Axis-B resolution-path tag}
   Confidence:       {Low / Medium / High}
   In short:         {1-2 sentences — what the issue is and why it got this verdict}
   Next action:      {one sentence — what the operator should do right now}
   Artifact:         products/{product}/triage/{ticket-id}-triage.md
   Outstanding data: {None | {N} items — see Section 7 of the artifact}
   ─────────────────────────────────────────────────────────
   ```

   Below the block, add one conditional line only if applicable — do not add both:
   - If Existing Defect: "Section 4 contains the defect deep-dive ready for the fix skill."
   - If Inconclusive: "Section 7 contains the Information Request Package — forward it to the reporter as a single round-trip."

8. Offer the operator a chance to provide additional information before closing:

   > "Do you have any additional information or context about this issue — logs,
   > config values, data records, or observations — that wasn't in the original
   > ticket? If so, share it now and I'll re-analyse with the new information
   > incorporated."

   If the operator provides new information: update the relevant session state
   sections (assumptions register, hypothesis ledger, IRR), re-run from the
   earliest stage the new information affects, and regenerate the artifact.
   If the operator has nothing to add or does not respond: proceed to Stage 8.

**The skill does not update Jira or any ticketing system.**
The operator decides what, if anything, to record there.

**Outputs:** completed triage artifact written to file; in-chat closing statement
delivered; operator offered opportunity to provide additional information.

---

## Stage 8 — Handoff
**Node:** N8
**Entry condition:** N7 output (`ArtifactRecord`) present — artifact confirmed written to disk
**Question:** What is the correct routing instruction for this classification — and is session state cleaned up?
**Allowed inputs:** `ClassificationRecord` from N6; `ArtifactRecord` from N7; session state file path
**Required output:** `HandoffRecord` — routing instruction delivered to operator; run complete
**Exit transition:** terminal — no further nodes

**Objective:** route the result correctly based on the classification.

**Actions:**

| Classification | Action |
|---|---|
| **Existing Defect** | Tell the operator: the defect deep-dive in Section 4 of the artifact is ready for the fix skill. The fix skill can consume it independently without re-running triage. Then check: if the classification rested on code evidence alone (Stage 3 was skipped or data was unavailable and the assumption "valid inputs/config assumed" is in the register), surface a corroboration prompt before closing — see corroboration protocol below. |
| **As Designed** | Return the artifact to the operator. The expectation correction explanation is in Section 6 of the artifact. Suggest wording for the reporter response if helpful. |
| **Not a Product Defect** | Return the artifact. The specific causal factor and resolution steps are in Section 6. |
| **Not Supported / Future Enhancement** | Return the artifact. The enhancement note is in Section 6. Suggest the operator log or link to a product enhancement backlog item. |
| **Any Inconclusive variant** | Return the artifact. The Information Request Package (Section 7) is the key deliverable — forward it to the reporter as a single round-trip request. |
| **Cannot start** | Return the specific missing floor item and the operator request. |

**Existing Defect — corroboration protocol (fires when classification rested on code evidence alone):**

If "valid inputs/config assumed" appears in the assumptions register, offer the
operator one corroboration step before closing: pull the anchor entity record or
relevant log to confirm the fault manifests on actual data. If confirmed, add as
a Good-to-have IRR item. If declined, note it was offered and waived.

**Outputs:** handoff instruction to operator.
