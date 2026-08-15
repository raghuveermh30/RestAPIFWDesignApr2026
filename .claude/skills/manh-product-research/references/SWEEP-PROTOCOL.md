# Sweep Protocol

Used by the `manh-product-research` orchestrator (SKILL.md Step 4) to determine
which existing lookup agent to spawn per layer, what inputs to pass, and how to
map research mode to each agent's parameters.

The orchestrator passes `output-destination: file` and an `output-path` to every
agent so full evidence is written to the cache directory — never returned inline
to the orchestrator.

---

## Layer → Agent delegation table

| Layer | Agent to spawn | Notes |
|---|---|---|
| tickets | `harness-ticket-lookup` | Glean-backed JIRA search |
| documents | `harness-document-lookup` | Glean-backed Confluence/doc search |
| source | `harness-source-lookup` | Clones repos, runs grep |
| industry | `harness-industry-lookup` | Web search + optional page fetch |

---

## Mode → agent parameter mapping

### Quick mode

| Layer | Agent input | Value |
|---|---|---|
| tickets | `mode` | `summarize` |
| documents | `query-type` | `broad-search` |
| source | (no mode param) | single existence-check question |
| industry | `query-type` | `broad-external` |

### Deep mode

| Layer | Agent input | Value |
|---|---|---|
| tickets | `mode` | `deep` |
| documents | `query-type` | `prior-decision` (run twice: broad + scoped to module from grounding-facts) |
| source | (no mode param) | existence question + structural question (two invocations) |
| industry | `query-type` | `industry-standard` (first), then `competitive-signal` (second) |

---

## Per-layer input construction

The orchestrator constructs these inputs from `idea`, `grounding-facts`, and `product`
before spawning each agent. Keep construction inline — it is a few words, not a
protocol.

### tickets layer

```
topic:               Derive from idea + grounding-facts — use product module names
                     where possible (e.g. "real-time supervisor visibility in Labor Management")
product:             {product}
project-key:         Read from products/{product}/repos.md; derive from slug if absent
mode:                quick → summarize | deep → deep
harness-lib:         {harness-lib}
output-destination:  file
output-path:         {cache-dir}/tickets-{slug}-{date}.md
```

### documents layer

```
question:            Derive from idea + grounding-facts as a prior-decision or design
                     question (e.g. "What prior decisions exist on supervisor dashboards
                     or real-time labor visibility in WMS?")
product:             {product}
query-type:          quick → broad-search | deep → prior-decision
harness-lib:         {harness-lib}
output-destination:  file
output-path:         {cache-dir}/documents-{slug}-{date}.md
```

### source layer

```
question:            Derive a concrete existence-check question from idea + grounding-facts
                     (e.g. "Does a real-time supervisor dashboard service or API exist
                     in component-lmcore or component-lminteraction?")
product:             {product}
repo-hints:          Extract component names from grounding-facts; map to repo names
                     via products/{product}/repos.md
                     Quick: 1-2 most central repos | Deep: all relevant repos
workspace:           {workspace} — ask user once per session if not already known
harness-lib:         {harness-lib}
output-destination:  file
output-path:         {cache-dir}/source-{slug}-{date}.md
```

### industry layer

```
topic:               Derive from idea scoped to supply chain domain
                     (e.g. "real-time labor floor visibility for warehouse supervisors")
product:             {product}
query-type:          quick → broad-external | deep → industry-standard
harness-lib:         {harness-lib}
output-destination:  file
output-path:         {cache-dir}/industry-{slug}-{date}.md
```

---

## What each agent returns to the orchestrator

When `output-destination: file`, every agent returns only a single compact line:

```
Lookup complete. [{mode|query-type|verdict} | {confidence}] — saved to {output-path}
```

The orchestrator collects these compact lines as each agent completes and surfaces
them to the user. The full evidence stays in the cache files for the synthesizer.

---

## Synthesizer inputs

After all layers complete, pass to `harness-research-synthesizer`:

```
idea, product, slug, date, mode, harness-lib
cache-dir:       {cache-dir}
layers-run:      comma-separated list of layers that completed successfully
compact-blocks:  all compact return lines concatenated (≤4 lines total)
grounding-facts: {grounding-facts from classifier}
```
