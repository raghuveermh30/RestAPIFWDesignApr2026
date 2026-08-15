---
name: manh-product-research
version: 2026-07-02
description: |
  Pre-explore orientation skill for Manhattan product areas. Use when you have a vague
  idea or problem statement and don't know where to start. Classifies the idea, determines
  which signal layers are worth sweeping, spawns parallel subagents to gather evidence,
  and produces an Orientation Brief that seeds the explore session with prior context —
  so the team doesn't start cold.

  Sits before manh-product-explore in the product development workflow:
  manh-product-research → manh-product-explore → manh-product-req-expand → manh-product-change-planner
trigger: manual
---

<what-to-do>

This skill runs in three execution modes. Read the invocation to determine which mode applies:

- **orchestrate** (default) — the user invokes the skill with an idea. Runs Steps 1-7.
- **classify** — invoked by the `harness-research-classifier` subagent. Runs the classification protocol and returns a compact record.
- **synthesize** — invoked by the `harness-research-synthesizer` subagent. Reads cache files and writes the Orientation Brief.

---

## Mode: orchestrate

Follow these steps in exact order. Do not skip steps or load context eagerly.
The orchestrator stays thin throughout — large context lives only inside subagents.

---

### Step 1: Accept input

Read the user's vague idea, problem statement, or topic. It does not need to be precise.
Extract:
- `idea`: the raw text as stated
- `product`: the product slug (e.g. `sif`, `wms`, `matm`). If not stated, ask once.
- `mode`: `quick` or `deep`. If not stated, ask using `ask_user_question`:

```
Question: "How deep should this research go?"
Header: "Research mode"
Options:
  - Quick — shallow sweep of recommended layers in parallel. Target: 5-10 min.
    Returns headlines and orientation pointers. Enough to walk into an explore session.
  - Deep — full sweep of all four layers in parallel. Target: 20-40 min.
    Multiple queries per layer, full document and source fetching, rich evidence.
    Use when you want to eliminate all orientational unknowns before explore.
```

Derive `date` as today's date (YYYY-MM-DD).

**OUTPUT FILENAME — MANDATORY FOR ALL MODELS:**

> The Orientation Brief filename format is non-negotiable. Every model must follow
> this exact format regardless of execution mode or any implicit convention.
> Do NOT use `{date}-{slug}` order. The required order is `{slug}-{date}`.

Derive `slug` using these rules (apply in order):
1. If a JIRA/ticket ID is present in the idea (pattern `[A-Z]+-\d+`, e.g. `POS-180099`), use it as the slug prefix: `{ticket-id}-{2-3-word-summary}` (e.g. `POS-180099-cfd-payment-breakdown`)
2. Otherwise, take the first 3–4 meaningful words of the idea, lowercase, joined with hyphens (strip articles, prepositions, punctuation)
3. All slugs: lowercase, kebab-case only — no spaces, underscores, or special characters

The output filename MUST be:
```
{product-dir}/research/{slug}-{date}-orientation.md
```

**Collision check (mandatory — all models):**
Before writing, check whether the file already exists:
```
{product-dir}/research/{slug}-{date}-orientation.md
```
- If it does NOT exist → write to that path
- If it DOES exist → append `-2`, then `-3`, etc. until a non-existing path is found:
  ```
  {product-dir}/research/{slug}-{date}-orientation-2.md
  {product-dir}/research/{slug}-{date}-orientation-3.md
  ```
- Never overwrite an existing Orientation Brief silently

Set `harness-lib` = the absolute path to this library (the directory containing
`harness-builder/`). Ask the user if you cannot determine it from context.

Read `{harness-lib}/products/product-registry.md`. Find the `repo` entry for `{product}`.
Construct: `product-dir` = `{workspace}/{product-repo}/products/{product}/`
If the slug is not found in the registry, halt and ask the user to add it before proceeding.

---

### Step 2: Classify the idea

Spawn `harness-research-classifier` as a **background subagent**.

Pass:
- `idea`: {idea}
- `product`: {product}
- `harness-lib`: {harness-lib}

Wait for it to return. It will return a compact classification record — approximately
200 tokens. Do not load any product artifacts yourself.

The classification record contains:
- `idea-type`: one of `new-capability` | `existing-gap` | `customer-pain` | `internal-tech` | `unknown`
- `recommended-layers`: list of layers recommended for Quick mode
- `grounding-facts`: 3-5 bullet facts extracted from product context
- `confidence`: HIGH | MEDIUM | LOW

---

### Step 3: Present layer plan and confirm

Using the classification record, present the proposed sweep plan to the user
with `ask_user_question`:

```
Question: "Based on the idea classification ({idea-type}), here is the proposed
research sweep. Confirm or adjust before we start."

Header: "Layer plan"
Options:
  - Run as recommended — {list recommended layers for Quick, all four for Deep}
  - Adjust layers — (user selects Other and specifies which layers to include/exclude)
```

Also show the grounding facts from the classifier so the user can see what
product context was already found.

Wait for confirmation. Record the final approved layer list as `approved-layers`.

---

### Step 4: Spawn layer subagents in parallel

Ensure the cache directory exists:
`{product-dir}/research/.cache/`

Load `references/SWEEP-PROTOCOL.md` now. It defines the agent to spawn per layer,
how to map research mode to each agent's parameters, and how to construct the
topic/question/repo-hints for each layer from `idea` and `grounding-facts`.

**Source layer confirmation (mandatory before spawning `harness-source-lookup`)**:

If `source` is in `approved-layers`, ask the user using `askUser()` before spawning it:

```
header: "Source lookup"
question: "The source layer will clone and search product repos for code-level evidence.
This can take several minutes. Do you want to include it?"
options:
  - label: "Yes, include source lookup (Recommended for Deep mode)"
    description: "Clone and search repos for existing implementations, APIs, and data models."
  - label: "Skip source lookup"
    description: "Proceed with tickets, documents, and industry layers only. Faster — you can always run source lookup separately."
```

- If the user confirms → keep `source` in the layer list and spawn `harness-source-lookup`
- If the user skips → remove `source` from `approved-layers` and do not spawn `harness-source-lookup`. Continue with remaining layers only.

For each layer in `approved-layers` (after the source confirmation above), spawn the
corresponding existing lookup agent as a **background subagent** simultaneously.
Do not wait for one before spawning the next.

| Layer | Agent |
|---|---|
| tickets | `harness-ticket-lookup` |
| documents | `harness-document-lookup` |
| source | `harness-source-lookup` |
| industry | `harness-industry-lookup` |

Every agent receives `output-destination: file` and `output-path` pointing to
the cache directory. See SWEEP-PROTOCOL.md for the full per-layer input set.

Tell the user: "Research sweep started across {N} layers in parallel. I'll report
as each layer completes."

---

### Step 5: Collect completion lines as subagents complete

As each layer agent completes, it returns a single compact line:

```
Lookup complete. [{mode|query-type|verdict} | {confidence}] — saved to {output-path}
```

Full evidence has already been written to the cache file by the agent.
The orchestrator never sees the full evidence — only this one-line summary.

For each completed layer, surface the line to the user immediately:

```
Layer complete: {layer-name} — {the compact line returned}
```

Collect all completion lines. After all approved layers complete, proceed to Step 6.

---

### Step 6: Spawn synthesis subagent

Spawn `harness-research-synthesizer` as a **foreground subagent**.

Pass:
- `idea`: {idea}
- `product`: {product}
- `slug`: {slug}
- `date`: {date}
- `mode`: {mode}
- `harness-lib`: {harness-lib}
- `cache-dir`: `{product-dir}/research/.cache/`
- `layers-run`: {comma-separated list of layers that completed}
- `completion-lines`: {all one-line completion summaries concatenated — ≤4 lines total}
- `grounding-facts`: {grounding-facts from classifier}

The synthesizer reads the full cache files itself, produces the Orientation Brief,
and saves it to:
`{product-dir}/research/{slug}-{date}-orientation.md`
(with `-2`, `-3`, etc. suffix if that path already exists — see collision check in Step 1)

It returns:
- `brief-path`: path to the saved brief
- `seeded-questions`: 3-5 questions ready to open the explore session with

---

### Step 7: Present output and next step

Tell the user:

```
Research complete.
Orientation Brief saved to: {brief-path}

Seeded questions for your explore session:
{seeded-questions — numbered list}

Recommended next step:
/manh-product-explore — use the Orientation Brief as pre-read context.
Pass it to the explore session so the interview starts with known signals,
not from zero.
```

---

## Mode: classify

Invoked by the `harness-research-classifier` subagent with a fresh context window.
Do not ask the user questions. Load context, classify, return the record, stop.

### Inputs received

- `idea`: {idea}
- `product`: {product}
- `harness-lib`: {harness-lib}

### Execution

**Step 1: Load the classification protocol**

Read:
`{harness-lib}/harness-builder/skills/manh-product-research/references/CLASSIFICATION-PROTOCOL.md`

**Step 2: Load product context as the protocol specifies**

The protocol defines exactly which files to load (module-index and functional-architecture).
Load only those files. Do not load anything else.

**Step 3: Classify and extract**

Execute all steps of the classification protocol against the idea and product.

**Step 4: Return**

Return exactly the CLASSIFICATION RECORD format defined in the protocol.
No other prose. No preamble. Just the record.

---

## Mode: synthesize

Invoked by the `harness-research-synthesizer` subagent with a fresh context window.
Do not ask the user questions. Read cache files, write the brief, return, stop.

### Inputs received

- `idea`: {idea}
- `product`: {product}
- `slug`: {slug}
- `date`: {date}
- `mode`: {mode}
- `harness-lib`: {harness-lib}
- `cache-dir`: {cache-dir}
- `layers-run`: {layers-run} — comma-separated list of layers that completed
- `completion-lines`: {completion-lines} — one-line completion summary per layer
- `grounding-facts`: {grounding-facts}

### Execution

**Step 1: Load the brief template and synthesis guidelines**

Read:
`{harness-lib}/harness-builder/skills/manh-product-research/references/ORIENTATION-BRIEF-TEMPLATE.md`

The synthesis guidelines are at the bottom of this file. Follow them exactly.

**Step 2: Read cache files**

For each layer in `layers-run`, read its cache file:

| Layer | Cache file |
|---|---|
| tickets | `{cache-dir}/tickets-{slug}-{date}.md` |
| documents | `{cache-dir}/documents-{slug}-{date}.md` |
| source | `{cache-dir}/source-{slug}-{date}.md` |
| industry | `{cache-dir}/industry-{slug}-{date}.md` |

Read each file fully. Use `completion-lines` only for the verdict/confidence signals in
the layers-swept table. Full evidence for synthesis comes from the cache files.

**Step 3: Synthesize and write the brief**

Following the template and synthesis guidelines, fill every section.
Write the "What we think this is about" section last (after synthesizing all layers)
but place it first in the document.

Ensure the directory exists: `{product-dir}/research/`

**MANDATORY FILENAME FORMAT — all models:**
The output filename MUST follow `{slug}-{date}-orientation.md` (slug first, then date).
Before writing, check for collisions — if the file exists, append `-2`, `-3`, etc.

Write the completed brief to:
`{product-dir}/research/{slug}-{date}-orientation.md`
(or `{slug}-{date}-orientation-2.md`, `-3.md`, etc. if that path already exists)

**Step 4: Return to orchestrator**

Return exactly this format. No other prose.

```
SYNTHESIS COMPLETE
------------------
brief-path: {product-dir}/research/{slug}-{date}-orientation.md
(use the actual resolved path including any -2/-3 suffix if collision occurred)

seeded-questions:
1. {Question 1}
2. {Question 2}
3. {Question 3}
[4. {Question 4}]
[5. {Question 5}]
```

</what-to-do>

<supporting-info>

## Inputs

| Input | Required | Description |
|---|---|---|
| `idea` | Yes | Vague idea, problem statement, or topic in natural language |
| `product` | Yes | Product slug (e.g. `sif`, `wms`, `matm`) |
| `mode` | No | `quick` or `deep`. Asked if not provided. |

## Token discipline — orchestrator rules

The orchestrator MUST NOT:
- Load module-index, functional-architecture, technical-architecture, or component-graph directly
- Accumulate full evidence blocks from subagents in its own context
- Pass large text blobs between steps

The orchestrator MUST:
- Delegate all context loading to subagents
- Collect only compact blocks (≤200 tokens per layer) from returning subagents
- Let the synthesizer read full evidence from cache files directly

## Output artifacts

| Artifact | Location |
|---|---|
| Orientation Brief | `{product-dir}/research/{slug}-{date}-orientation.md` (or `{slug}-{date}-orientation-2.md` etc. on collision) |
| Layer evidence cache | `{product-dir}/research/.cache/{layer}-{slug}-{date}.md` |

The `.cache/` files are retained after the session for traceability and potential
Deep follow-up without re-running sweeps.

## When to use

- You have a vague idea and don't know where to start
- The team is considering a new capability and has no prior context
- A customer request has come in and you want to understand the landscape before committing to explore
- You want to avoid an explore session that re-discovers things that are already built or already decided

## When NOT to use

- You already have a clear, specific question → use the individual lookup skills directly
- You have an approved explore doc → go straight to `manh-product-req-expand`
- The idea is well-understood by the team → skip to `manh-product-explore` directly

## Relationship to other skills

| Skill | Relationship |
|---|---|
| `manh-product-explore` | Downstream — consumes the Orientation Brief as pre-read |
| `manh-product-ticket-lookup` | Delegated to via `harness-ticket-lookup` subagent |
| `manh-product-document-lookup` | Delegated to via `harness-document-lookup` subagent |
| `manh-product-source-lookup` | Delegated to via `harness-source-lookup` subagent |
| `manh-product-industry-lookup` | Delegated to via `harness-industry-lookup` subagent |

## References

| File | Load when |
|---|---|
| `references/CLASSIFICATION-PROTOCOL.md` | Loaded in `classify` mode only — do not load in orchestrate mode |
| `references/SWEEP-PROTOCOL.md` | Loaded in orchestrate mode Step 4 only |
| `references/ORIENTATION-BRIEF-TEMPLATE.md` | Loaded in `synthesize` mode only — do not load in orchestrate mode |

## Subagent architecture

| Subagent | Mode invoked | Purpose |
|---|---|---|
| `harness-research-classifier` | `classify` | Fresh context boundary for product context loading; invokes this skill in classify mode |
| `harness-research-synthesizer` | `synthesize` | Fresh context boundary for reading full cache files; invokes this skill in synthesize mode |
| `harness-ticket-lookup` | — | Fresh context boundary; invokes `manh-product-ticket-lookup` |
| `harness-document-lookup` | — | Fresh context boundary; invokes `manh-product-document-lookup` |
| `harness-source-lookup` | — | Fresh context boundary; invokes `manh-product-source-lookup` |
| `harness-industry-lookup` | — | Fresh context boundary; invokes `manh-product-industry-lookup` |

All logic lives in the skill and its reference files. Subagents are pure delegation
wrappers — they provide a fresh context window and handle the file output mechanism.
They contain no protocol logic of their own.

</supporting-info>
