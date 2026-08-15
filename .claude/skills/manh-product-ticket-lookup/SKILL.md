---
name: manh-product-ticket-lookup
version: 2026-07-02
description: |
  On-demand JIRA ticket lookup for Manhattan product areas. Given a topic and
  project scope, searches the ticket index and returns a structured evidence block.
  Three modes: Summarize (narrative synthesis), Quick (JSON list with summary
  fields), Deep (full detail — description, comments, custom fields).
trigger: manual
---

<what-to-do>

### Step 0: Resolve product-dir

Identify `{product}` slug from the user's question or context. Ask once if not stated.
Set `harness-lib` = the repo containing this skill (`genai-productivity-lib/`).
Read `{harness-lib}/products/product-registry.md`. Find `repo` for `{product}`.
Construct: `product-dir` = `{workspace}/{product-repo}/products/{product}/`
If the slug is not in the registry, halt and ask the user to add it before proceeding.

### Step 1: Determine mode

If the input contains an explicit prefix, use it directly:

| Input starts with | Mode |
|---|---|
| `summarize:` | Summarize |
| `quick:` | Quick |
| `deep:` | Deep |

If no prefix is given, infer the mode from the prompt using these signals:

| Signal in the prompt | Inferred mode |
|---|---|
| "what's going on", "landscape", "themes", "patterns", "overview", "tell me about" | Summarize |
| "list", "how many", "show me tickets", "find tickets", "count", "which tickets" | Quick |
| "details", "full description", "comments", "workarounds", "what did they say", "deep dive", "everything about" | Deep |
| Ambiguous — no clear signal | Summarize |

### Step 2: Confirm mode with the engineer

Present the three modes using `ask_user_question`. Mark the inferred mode as
recommended. Always do this step — even when a prefix was given, confirmation
avoids running an expensive Deep lookup by accident.

```
Question: "Which lookup mode do you want for '{topic}'?"
Header: "Lookup mode"
Options:
  - Summarize — narrative synthesis across tickets, grouped by theme. Good for
    understanding the landscape before forming specific questions. (recommended
    if inferred)
  - Quick — JSON list of matching tickets with summary fields. Good for counting,
    scanning, and building a signal inventory. (recommended if inferred)
  - Deep — full detail on all matching tickets: description, comments, custom
    fields, paginated in batches of 5. Use when you need raw evidence. (recommended
    if inferred)
```

Wait for the engineer's confirmation before proceeding.

### Step 2b: Confirm output destination

In the same `ask_user_question` call as Step 2 (use two questions in one call),
also ask:

```
Question: "Where should the results be saved?"
Header: "Output"
Options:
  - Inline only — print results in the conversation
  - Save to file — write to {product-dir}/ticket-lookups/ and print a summary inline
```

If "Save to file" is chosen, derive the filename as:
`{mode-slug}-{topic-slug}-{YYYY-MM-DD}.md`

where `topic-slug` is the topic lowercased with spaces replaced by hyphens
(e.g., `deep-uom-handling-2026-06-06.md`).

Ensure the directory `{product-dir}/ticket-lookups/` exists before writing
(create it if absent).

### Step 3: Load and execute the protocol

Load the protocol file for the confirmed mode only. Do not load all three at once.

| Mode | Protocol file |
|---|---|
| Summarize | `references/SUMMARIZE-PROTOCOL.md` |
| Quick | `references/QUICK-PROTOCOL.md` |
| Deep | `references/DEEP-PROTOCOL.md` |

Execute the protocol. The protocol's final step handles delivery (inline or file)
based on the output destination confirmed in Step 2b.

After delivering results, offer to run a follow-up lookup in a different mode if
the results are insufficient.

</what-to-do>

<supporting-info>

## Inputs

| Input | Required | Description |
|---|---|---|
| `topic` | Yes | The subject to research in natural language (e.g. "UOM handling in store fulfillment") |
| `product` | Yes | Product slug (e.g. `sif`, `wms`, `matm`) |
| `project-key` | Yes | JIRA project key (e.g. `SIF`, `METIS`) |
| `issue-types` | No | Comma-separated: `bug`, `story`, `CII`, `product-request`, `enhancement`. Defaults to all types. |
| `time-range` | No | Natural language time range (e.g. "last 18 months"). Defaults to no restriction. |
| `status` | No | Status filter (e.g. "open", "closed", "answered"). Defaults to all statuses. |

## When NOT to use

- Answer is visible in `functional-architecture.md`, `module-index.md`, or
  `component-graph.json` — read those first.
- Need source code evidence — use `manh-product-source-lookup` instead.
- Need Confluence or internal design docs — use `manh-product-document-lookup` instead.

</supporting-info>
