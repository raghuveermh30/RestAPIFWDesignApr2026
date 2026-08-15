---
name: manh-product-industry-lookup
version: 2026-07-02
description: |
  On-demand external research lookup for Manhattan product areas. Given a topic
  and domain context, searches for industry standards, domain best practices, and
  competitive signals. Returns a structured evidence block (verdict, cited sources,
  key findings, confidence).
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
| "industry standard", "best practice", "how do others do this", "common approach" | Industry standard |
| "competitor", "how does X handle", "what does SAP/Blue Yonder/Oracle do" | Competitive signal |
| "market trend", "direction", "where is the industry going", "emerging" | Market trend |
| Ambiguous — no clear signal | Broad external |

### Step 2: Confirm query type and output destination

Use `ask_user_question` with two questions in one call:

```
Question 1: "What type of external research are you looking for?"
Header: "Research type"
Options:
  - Industry standard — what is the accepted approach in WMS/TMS/supply chain for this problem
  - Competitive signal — how named competitors handle this (SAP, Blue Yonder, Oracle, etc.)
  - Market trend — where the industry is heading; emerging patterns and approaches
  - Broad external — search broadly across all external sources
  (mark inferred type as recommended)

Question 2: "Where should results be saved?"
Header: "Output"
Options:
  - Inline only — print evidence block in the conversation
  - Save to file — write to {product-dir}/industry-lookups/ and print a summary inline
```

If "Save to file" is chosen, derive the filename as:
`{query-type-slug}-{topic-slug}-{YYYY-MM-DD}.md`
(e.g., `industry-standard-uom-handling-2026-06-07.md`)

Ensure the directory `{product-dir}/industry-lookups/` exists before writing.

### Step 3: Execute the search protocol

Load and follow `references/SEARCH-PROTOCOL.md`. The protocol covers query
construction per type, result evaluation, confidence assessment, and delivery.

After delivering results, offer a follow-up search with a different query type if
the results are insufficient.

</what-to-do>

<supporting-info>

## Inputs

| Input | Required | Description |
|---|---|---|
| `topic` | Yes | The subject to research in natural language |
| `product` | Yes | Product slug (e.g. `sif`, `wms`, `matm`) — used for domain context |
| `domain` | No | Supply chain domain to scope the search (e.g. `store fulfillment`, `warehouse management`, `transportation`). Defaults to inferred from product slug. |
| `time-range` | No | Natural language time range (e.g. "last 3 years"). Defaults to no restriction. |

## When NOT to use

- Looking for internal Manhattan documents or prior decisions — use `manh-product-document-lookup` instead.
- Looking for JIRA tickets or customer-reported issues — use `manh-product-ticket-lookup` instead.
- Looking for source code evidence — use `manh-product-source-lookup` instead.

## Note on Deep mode

This skill is primarily invoked in `manh-product-research` Deep mode (Phase 3).
It is rarely needed in Quick mode — external benchmarking adds the most value
when internal signals have already been gathered (Phases 1 and 2).

</supporting-info>
