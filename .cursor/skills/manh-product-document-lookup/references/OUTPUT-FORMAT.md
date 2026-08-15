# Document Lookup Output Format

Evidence block template for `manh-product-document-lookup`. Used for both inline
delivery and file output.

---

## Evidence block

```markdown
## Document Lookup: "{question}"
Type: {Prior decision | Design context | Reference doc | Discussion / notes | Broad search}
Product: {product}  Scope: {scope | "product-wide"}
Searched: {YYYY-MM-DD}

### Answer
{Narrative response from the search — paste verbatim, preserving structure.
 Strip trailing metadata (chatId, traceId, workflowRunId).}

### Documents Cited
| Title | Summary | URL |
|---|---|---|
| {title} | {one-sentence summary of what this doc contributes} | {url | "—"} |

### Gaps
{What this search could not answer — questions that need a different lookup type
 or a follow-up search. Write "None" if the question was fully answered.}

Confidence: {HIGH | MEDIUM | LOW}
```

---

## Confidence rules

| Condition | Confidence |
|---|---|
| 3+ on-topic documents cited, question clearly answered | HIGH |
| 1–2 documents cited, or answer is partial | MEDIUM |
| No documents found, or results are off-topic | LOW |

---

## Gaps field guidance

The Gaps field is consumed by `manh-product-research` to drive follow-up lookups.
Be specific — name the unanswered sub-question, not just "more information needed".

Good: "Could not find whether the 2024 UOM redesign decision was ever ratified — try a ticket lookup for the design review."
Bad: "Some information may be missing."
