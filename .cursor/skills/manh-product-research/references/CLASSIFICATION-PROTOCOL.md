# Classification Protocol

Used exclusively by `harness-research-classifier`. Do not load in the orchestrator.

---

## Purpose

Classify a vague idea into a type, recommend which signal layers to sweep in Quick mode,
and extract grounding facts from product context — all in a single subagent pass.
Return a compact classification record (~200 tokens). Discard all loaded context on exit.

---

## Step 1: Load product context (targeted)

Load these two files only. Do not load technical-architecture, component-graph, or
any repo-level artifacts.

1. `products/{product}/module-index.md` — capability map; use to check if anything
   analogous to the idea already exists
2. `products/{product}/functional-architecture.md` — business capability hierarchy;
   use to understand where in the product the idea might live

If `module-index.md` does not exist, fall back to scanning
`products/{product}/modules/*.md`. Load no more than 5 module files.

If `functional-architecture.md` does not exist, proceed without it.

---

## Step 2: Classify the idea

Match the idea against these types using the signals below. Pick the single best fit.

| Idea type | Signals |
|---|---|
| `new-capability` | Nothing analogous exists in module-index. No prior module or feature covers this. The idea introduces a capability the product does not have. |
| `existing-gap` | Something related exists in module-index but is incomplete, limited, or doesn't handle the specific scenario described. |
| `customer-pain` | The idea is framed as a reported problem, complaint, or customer request. Words like "customers are asking", "this keeps coming up", "pain point". |
| `internal-tech` | The idea is about architecture, refactoring, performance, debt, or infrastructure. Not customer-facing. |
| `unknown` | Cannot confidently classify. The idea is too vague or spans multiple types. |

---

## Step 3: Determine recommended layers for Quick mode

Apply this table to the classified idea type:

| Idea type | Recommended layers (Quick) | Rationale |
|---|---|---|
| `new-capability` | source, industry | Tickets/docs won't have signal for something novel. Source confirms baseline. Industry gives benchmarks. |
| `existing-gap` | tickets, documents, source | All internal layers. Industry adds context but is secondary for gaps. |
| `customer-pain` | tickets, industry | What customers reported + what others do. Source is secondary. |
| `internal-tech` | source, documents | Prior decisions and current code are the relevant signals. |
| `unknown` | tickets, documents, source, industry | Cannot assume; sweep all. |

Deep mode always runs all four layers regardless of this recommendation.

---

## Step 4: Extract grounding facts

From the loaded product context, extract 3-5 facts most relevant to the idea.
Be specific — name actual modules, features, or capabilities by their exact names
from the artifacts. Do not generalize.

Format:
```
- {module/feature name}: {one sentence describing what it does and how it relates to the idea}
```

If nothing relevant exists, say so explicitly:
```
- No existing module or capability found directly related to this idea.
```

---

## Step 5: Return the classification record

Return exactly this format. No other prose.

```
CLASSIFICATION RECORD
---------------------
idea-type: {new-capability|existing-gap|customer-pain|internal-tech|unknown}
confidence: {HIGH|MEDIUM|LOW}
recommended-layers: {comma-separated: tickets, documents, source, industry}

grounding-facts:
- {fact 1}
- {fact 2}
- {fact 3}
[- {fact 4}]
[- {fact 5}]

rationale: {One sentence explaining the classification choice.}
```

Keep the entire record under 250 tokens.

---

## Confidence scoring

| Confidence | Condition |
|---|---|
| HIGH | Module-index clearly confirms or denies the idea. Classification is unambiguous. |
| MEDIUM | Some relevant modules found but partial match. Classification is a best-fit inference. |
| LOW | No relevant context found. Product artifacts don't cover this area. Classification is a guess. |
