---
name: manh-issue-triage
version: 2026-07-02
description: |
  Use when a defect, customer issue, or unexpected behavior needs structured investigation
  before a root cause is known. Takes a JIRA ticket or free-text description
  and classifies it as Existing Defect, As Designed, Not a Product Defect, Not Supported,
  or Inconclusive — with a full evidence-backed explanation. For confirmed defects, produces
  a technical deep-dive ready for the fix skill. Do not use when the fix approach is already
  known — use the repo or product change-planner instead.
trigger: manual
---

<what-to-do>

## Invocation

```
/manh-issue-triage {ticket-id}
/manh-issue-triage {ticket-id} product={product}
/manh-issue-triage "{free-text description of the issue}"
/manh-issue-triage resume: {ticket-id}
/manh-issue-triage triage-report: {ticket-id}
/manh-issue-triage triage-report: {ticket-id} product={product}
```

**Examples:**
```
/manh-issue-triage OM-12345
/manh-issue-triage OM-12345 product=matm
/manh-issue-triage "FedEx being selected over UPS Ground for zone 5 shipments"
/manh-issue-triage resume: OM-12345
/manh-issue-triage triage-report: OM-12345
/manh-issue-triage triage-report: OM-12345 product=matm
```

---

## Step 1 — Parse input and resolve product

### 1.1 Detect mode

If input starts with `resume:` → resume a paused session. If input starts with `triage-report:` → generate artifact from existing session state. If input matches `[A-Z]+-\d+` → full triage run, fetch ticket. Otherwise → full triage run, use description as input.

### 1.2 Fetch the ticket (if ticket ID provided)

Use whatever JIRA search or lookup tool is available (JIRA MCP tool, Glean, manh-bridge,
or equivalent). If no tool is available, ask the operator to paste the ticket content
(description, comments, attachments, linked issues).

### 1.3 Resolve the product

Set `harness-lib` = the repo containing this skill (`genai-productivity-lib/`).
Read `{harness-lib}/products/product-registry.md` for the full list of product slugs
and their Jira project keys.

Apply in this priority order:

1. **Explicitly provided** (`product=matm`) → use it directly
2. **Ticket ID provided, product not stated** → extract the Jira project key from the
   ticket ID (e.g. `OM-12345` → `OM`). Match it against the Jira Project Key column in
   `product-registry.md` to find the product slug. Present the match to the operator
   for confirmation.
3. **Free-text description, no product stated** → for each product slug in the registry,
   read its `{product-dir}/module-index.md` briefly; find best keyword match against
   the description. Present the match to the operator for confirmation.
4. **Cannot determine** → list the available product slugs from the registry and ask
   the operator to pick:
   ```
   I wasn't able to determine the product from the ticket/description.
   Available products: {list from product-registry.md}
   Which product does this issue belong to?
   ```

Set `product` = confirmed product slug. Use it as `{product}` throughout.

Find `repo` for `{product}` in `product-registry.md`.
Construct: `product-dir` = `{workspace}/{product-repo}/products/{product}/`
If the slug is not in the registry, halt and ask the user to add it before proceeding.

### 1.4 Derive session key

- If ticket ID present: `session-key` = ticket ID (e.g. `OM-12345`)
- If free text: derive from first 3–4 meaningful words, lowercase, kebab-case
  (e.g. "fedex-over-ups-zone5")

### 1.5 Check for existing session state (resume support)

Check whether `{product-dir}/triage/.session-{session-key}.md` exists.

- **If `resume:` mode was invoked:**

  **SKILL.md Step 3 (Initialize session state) must be skipped entirely on resume —
  the existing session state file must never be overwritten.**

  1. Read `{product-dir}/triage/.session-{session-key}.md` from disk.
     If not found: tell the operator "No session state found for {ticket-id} —
     start a fresh run." **STOP — do not proceed to any further step under any circumstances.**
  2. Re-read each file listed in Section 2 `modules-loaded`. If any file is
     missing, note it to the operator and continue.
  3. Re-read the `locator` field of every predicate in Section 9 with
     `result: UNKNOWN`. If any location is missing, note it and continue.
  4. Reset `iteration-count` to 0 in session state.
  5. Present to the operator:
     > "Resuming {ticket-id} from {controller.current-node}.
     > Leading hypothesis: {highest confidence-score hypothesis, or 'none yet'}
     > Open predicates: {count UNKNOWN predicates}
     > Outstanding IRR items: {count}
     > {Any files that could not be re-read}
     > Confirm to continue or correct anything before I proceed."
  6. **MANDATORY STOP — do not re-enter `controller.current-node` until the operator
     has explicitly confirmed. Never auto-proceed on resume — the operator must
     verify the re-entry state is correct before investigation continues.**
     Wait for operator confirmation then re-enter `controller.current-node`.
- **If `triage-report:` mode was invoked:**

  1. Resolve `product`: if explicitly provided use it; otherwise scan
     `products/*/triage/.session-{session-key}.md` across all product directories
     to locate the session state file, then read `product` from its YAML header.
     If not found: tell the operator "No session state found for {ticket-id} —
     cannot generate report." **STOP — do not proceed.**
  2. Check whether `{product-dir}/triage/{session-key}-triage.md` already exists.
     If it exists: **MANDATORY STOP — do not overwrite the existing report without
     explicit operator confirmation. Present the path and ask "A triage report already
     exists at {path}. Overwrite it? (yes / no)" and wait for the response.
     If no or no response: do not proceed. If yes: proceed.**
  3. Load `references/STAGE-PROTOCOLS.md` from disk now — required for Stage 7 pre-flight protocol.
  4. **Skip Steps 2–5. Proceed directly to Step 6 (artifact generation).**

- **If full triage mode but file exists:** tell the operator a prior session exists
  for this ticket and offer to resume it or start fresh.
- **If no file exists:** continue to Step 2.

Ensure the triage directory exists: `{product-dir}/triage/`

---

## Step 2 — Load stage protocols

Load `references/STAGE-PROTOCOLS.md` now. This file contains the full execution
detail for Stages 0–8 and must be loaded before beginning any stage.

Do **not** load `references/CLASSIFICATION-LOGIC.md` yet — it is loaded just before
Stage 6 to avoid unnecessary context consumption.

---

## Step 3 — Initialize session state

**Skip this step entirely if `resume:` or `triage-report:` mode was invoked — the
session state file already exists and must not be overwritten.**

Using the template in `references/SESSION-STATE-TEMPLATE.md`, create the session
state file at:
`{product-dir}/triage/.session-{session-key}.md`

Populate the YAML header:
```yaml
ticket-id: "{session-key}"
product: "{product}"
session-key: "{session-key}"
stage-reached: 0
stage-status: "in-progress"
iteration-count: 0
iteration-cap: 5
operator-extended-budget: null
started: "{YYYY-MM-DD HH:MM}"
last-updated: "{YYYY-MM-DD HH:MM}"
controller:
  current-node: "N0"
  last-completed-node: null
  last-completed-output-type: null
  pre-router-active: false
```

Update the session state file after every stage gate completes.

---

## Step 4 — Execute stages 0–8

Follow `references/STAGE-PROTOCOLS.md` exactly. Execute stages in sequence.
Do not skip stages (except where a gate explicitly permits — e.g. Stage 3 is
conditional on Gate G2b).

### Data source priority order (apply at every stage that fetches external data)

**For product documentation:**
1. `{product-dir}/module-index.md` — primary product knowledge source
2. `{product-dir}/modules/{area}.md` — area detail; follow any references to related artifacts found within
3. `{product-dir}/functional-architecture.md` — business capability context
4. `{product-dir}/technical-architecture.md` — service/component inventory
5. Confluence / document search tool (available search tool, Glean, or equivalent) — governing specs, design docs, knowledge pages. If no tool: ask operator to provide.

**For code:**
1. Read `{product-dir}/repos.md` to identify relevant repos. For each, verify
   the path exists on disk before anything else. Use local files directly via Glob,
   Grep, and Read.
2. Before scanning a repo, check for `.manh-ai-harness/` in the repo root — if
   present, it contains harness artifacts (repo-analysis.md, entity-analysis-report.md,
   key-learnings.md, and others) that describe the repo's structure, entities, and
   known patterns. Use what's relevant to the symptom rather than scanning the repo
   cold.
3. If a repo path is not found locally, ask the operator: "I couldn't find
   `{repo-name}` locally — do you have it at a different path, or should I
   search Bitbucket?" Use whichever the operator confirms.

**For tickets / similarity scan:**
1. Available JIRA search tool, Glean JIRA search, manh-bridge, or equivalent
2. Linked issues on the ticket itself
3. If no tool: ask operator if they are aware of similar known tickets or prior CIIs

**For customer data / config:**
1. Ask operator — they may have direct access or can relay to the customer
2. If unavailable: proceed under a stated assumption, lower affected hypothesis confidence, add to IRR

### State persistence rule

After every stage gate, update `{product-dir}/triage/.session-{session-key}.md`
with the current stage reached, gate decision, and all accumulated state
(assumptions register, hypothesis ledger, IRR, completeness scorecard).

### Iteration budget

Track `iteration-count` in session state. Default cap: **5 cycles** (shared across
Stage 3↔4 and Stage 4↔5 loops). On reaching the cap, follow the budget-exhaustion
protocol in STAGE-PROTOCOLS.md — do not stop silently.

---

## Step 5 — Load classification logic (before Stage 6 only)

Just before executing Stage 6, load `references/CLASSIFICATION-LOGIC.md`.
Follow its two-axis model, label decision logic, config sub-tree, and regression gate.

---

## Step 6 — Generate and write the output artifact (Stage 7)

**MANDATORY STOP — Stage 7 (artifact generation) must execute before Stage 8 begins
under any circumstances. The triage report MUST be written to disk. Do not render
the closing summary, do not proceed to Step 7, until the file
`{product-dir}/triage/{session-key}-triage.md` exists on disk.**

**Compact-resilience check:** before executing Stage 7, read
`references/OUTPUT-ARTIFACT-TEMPLATE.md` from disk now — never from memory.
Also read `{product-dir}/triage/.session-{session-key}.md` from disk now.
If either cannot be read, stop and tell the operator before proceeding.
Stage 7 in `references/STAGE-PROTOCOLS.md` contains the full pre-flight protocol.

Using `references/OUTPUT-ARTIFACT-TEMPLATE.md`, produce the triage artifact.
Use the session state file as the primary data source for all sections.

Write to: `{product-dir}/triage/{session-key}-triage.md`

After writing, render the closing Quick Summary block to the terminal (see Stage 7
step 7 in STAGE-PROTOCOLS.md). This is the sole in-chat closing output.

**The skill does not write to Jira or any ticketing system.**
The operator decides what, if anything, to record there.

---

## Step 7 — Handoff (Stage 8)

Follow the handoff routing table in STAGE-PROTOCOLS.md Stage 8.

</what-to-do>

<supporting-info>

## Inputs

| Input | Required | Description |
|---|---|---|
| Ticket ID or description | Yes | JIRA ticket ID (`OM-12345`) or free-text description |
| `product` | No | Product slug (e.g. `matm`, `wms`, `sif`). Inferred if not provided; confirmed with operator. |
| `resume:` prefix | No | Resume a paused session from its last gate |
| `triage-report:` prefix | No | Generate or regenerate the triage artifact from existing session state |

## Output artifacts

| Artifact | Location |
|---|---|
| Triage report | `{product-dir}/triage/{ticket-id}-triage.md` |
| Session state | `{product-dir}/triage/.session-{ticket-id}.md` |

## Classification labels

| Label | When |
|---|---|
| Existing Defect | Base code does not behave per documented contract |
| As Designed | Behavior matches documented contract; no supported toggle changes it |
| Not a Product Defect | Customer-side factor (config/data/user/environment/custom code) explains it |
| Not Supported / Future Enhancement | Legitimate gap; not achievable by any supported configuration |
| Inconclusive (variants) | Evidence does not support a label at the required confidence |

## References (loaded on demand — not all at once)

| File | Load when |
|---|---|
| `references/STAGE-PROTOCOLS.md` | Step 2 — start of every run |
| `references/CLASSIFICATION-LOGIC.md` | Step 5 — just before Stage 6 only |
| `references/OUTPUT-ARTIFACT-TEMPLATE.md` | Stage 7 — artifact generation |
| `references/SESSION-STATE-TEMPLATE.md` | Step 3 — session initialization |

## When to use

- A defect ticket (JIRA CII or internal report) needs structured analysis
  before a classification is committed
- A ticket has been bounced between teams without a clear root cause
- The team needs a defensible, evidence-backed classification to share with a customer
- A developer wants a structured starting point before diving into code

## When NOT to use

- The fix approach is already known and approved — go straight to the repo's
  change-planner skill
- The ticket is clearly a duplicate of a known open issue (trivial match) — merge
  directly without full triage
- The issue is a data-correction or config-change request with no ambiguity

</supporting-info>
