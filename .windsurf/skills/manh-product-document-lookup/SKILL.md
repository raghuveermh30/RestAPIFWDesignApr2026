---
name: manh-product-document-lookup
version: 2026-07-02
description: |
  On-demand internal document lookup for Manhattan product areas. Given a question
  and product scope, searches Confluence pages, design docs, architecture decision
  records, and prior engineering notes. Returns a structured evidence block
  (verdict, cited documents, key excerpt, confidence).
trigger: manual
---

<what-to-do>

### Step 0: Resolve product-dir

Identify `{product}` slug from the user's question or context. Ask once if not stated.
Set `harness-lib` = the repo containing this skill (`genai-productivity-lib/`).
Read `{harness-lib}/products/product-registry.md`. Find `repo` for `{product}`.
Construct: `product-dir` = `{workspace}/{product-repo}/products/{product}/`
If the slug is not in the registry, halt and ask the user to add it before proceeding.

### Step 1: Determine query type

Classify the question into one of these query types to guide search construction:

| Signal in the question | Query type |
|---|---|
| "was this tried before", "prior attempt", "previous decision", "why was X chosen" | Prior decision |
| "design doc", "architecture", "how does X work internally", "technical overview" | Design context |
| "confluence", "wiki", "internal doc", "runbook", "guide", "how to" | Reference doc |
| "what did engineering say", "meeting notes", "discussion", "thread" | Discussion / notes |
| Ambiguous — no clear signal | Broad search |

### Step 2: Confirm query type and output destination

Use `ask_user_question` with two questions in one call:

```
Question 1: "What type of document are you looking for?"
Header: "Doc type"
Options:
  - Prior decision — why something was built the way it was; ADRs, rejected alternatives
  - Design context — how a feature or system works; architecture overviews, design docs
  - Reference doc — runbooks, setup guides, how-to pages, Confluence wikis
  - Broad search — search broadly and surface what's relevant
  (mark inferred type as recommended)

Question 2: "Where should results be saved?"
Header: "Output"
Options:
  - Inline only — print evidence block in the conversation
  - Save to file — write to {product-dir}/document-lookups/ and print a summary inline
```

If "Save to file" is chosen, derive the filename as:
`{query-type-slug}-{topic-slug}-{YYYY-MM-DD}.md`
(e.g., `prior-decision-uom-handling-2026-06-07.md`)

Ensure the directory `{product-dir}/document-lookups/` exists before writing.

### Step 3: Execute the search protocol

Load and follow `references/SEARCH-PROTOCOL.md`. The protocol covers message
construction per query type, result parsing, confidence assessment, and delivery.

After delivering results, offer a follow-up search with a different query type if
the results are insufficient.

</what-to-do>

<supporting-info>

## Inputs

| Input | Required | Description |
|---|---|---|
| `question` | Yes | The specific thing to look up in natural language |
| `product` | Yes | Product slug (e.g. `sif`, `wms`, `matm`) |
| `scope` | No | Narrow the search: module name, team name, or Confluence space. Defaults to product-wide. |
| `time-range` | No | Natural language time range (e.g. "last 2 years"). Defaults to no restriction. |

## When NOT to use

- Looking for JIRA tickets, CIIs, or defects — use `manh-product-ticket-lookup` instead.
- Looking for source code evidence — use `manh-product-source-lookup` instead.
- Answer is already visible in `functional-architecture.md`, `module-index.md`, or
  `component-graph.json` — read those harness artifacts first.

</supporting-info>
