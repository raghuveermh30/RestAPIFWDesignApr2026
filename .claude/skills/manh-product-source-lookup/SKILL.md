---
name: manh-product-source-lookup
version: 2026-05-21
description: |
  On-demand source code lookup for Manhattan product repos. Given a specific question
  and caller-provided repo hints, clones the minimal required repos shallowly, classifies
  the question type, runs a targeted search, and returns a structured evidence block
  (verdict, file path, snippet, confidence). Invoked directly by engineers or delegated
  to as a subagent by manh-product-explore, manh-product-req-expand,
  manh-product-change-planner, and manh-product-code-reviewer when artifact-level context
  is insufficient to answer a question with confidence.
trigger: manual
---

<what-to-do>

Resolve a specific code-level question by searching source in the minimal set of repos
needed to answer it. Delegate to the `harness-source-lookup` subagent when subagent
delegation is available. Fall back to inline execution otherwise.

Ask for workspace path once per session — reuse for all subsequent lookups. If invoked
from within manh-product-explore or manh-product-req-expand, record workspace and cloned
repos in the calling transcript under a `## Source Lookup Context` section.

Follow the full lookup protocol in [LOOKUP-PROTOCOL.md](./references/LOOKUP-PROTOCOL.md).

When invoked as a subagent, return the evidence block for the caller to embed inline.
When invoked directly by an engineer, present the evidence block and offer follow-up lookups.

</what-to-do>

<supporting-info>

## Inputs

| Input | Required | Description |
|---|---|---|
| `question` | Yes | The specific thing to verify in natural language |
| `product` | Yes | Product name (e.g. `sif`) — used to locate `repos.md` |
| `repo-hints` | Yes | Repos the caller believes are relevant. Caller is responsible for inference — this skill does not discover repos independently. |
| `workspace` | Session | Path to local repos (e.g. `~/Dev/manh`). Asked once, reused for session. |

## When NOT to use

- The answer is visible in `functional-architecture.md`, module docs, or
  `component-graph.json` — read those first before escalating to source.
- A full repo analysis is needed — use `manh-repo-analyzer` instead.

## Subagent

Agent spec: `harness-builder/agents/harness-source-lookup.md`

</supporting-info>
