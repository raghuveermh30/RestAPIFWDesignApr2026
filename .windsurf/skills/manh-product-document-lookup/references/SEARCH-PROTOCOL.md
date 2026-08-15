# Document Search Protocol

Executes a document search for a given question using the internal search backend.
Returns a structured evidence block per `OUTPUT-FORMAT.md`.

---

## Backend

Invoke `mcp_-_datasearch` on the `glean` MCP server for all queries.

**Message construction rules (apply to all query types):**
- Single concise paragraph — no bullet points, no newlines inside the message
- Keep under 150 characters where possible; never exceed 200
- Always name the product or module to narrow scope
- Never mention Glean or MCP in the message itself

---

## Step 1: Build the query message

Construct the message based on the confirmed query type:

### Prior decision

```
Search Confluence for prior decisions or architecture decision records about {topic} in {product}{if scope: , {scope}}{if time-range: , from {time-range}}. Why was the current approach chosen? What alternatives were rejected?
```

### Design context

```
Search Confluence for design documents or technical overviews of {topic} in {product}{if scope: , {scope}}{if time-range: , from {time-range}}. How does it work? What are the key components?
```

### Reference doc

```
Search Confluence for guides, runbooks, or reference pages about {topic} in {product}{if scope: , {scope}}. Return page titles, links, and a brief summary of each.
```

### Discussion / notes

```
Search Confluence for meeting notes, engineering discussions, or internal threads about {topic} in {product}{if scope: , {scope}}{if time-range: , from {time-range}}.
```

### Broad search

```
Search Confluence for any internal documents about {topic} in {product}{if scope: , {scope}}{if time-range: , from {time-range}}. Summarize what you find.
```

---

## Step 2: Call the search tool

Invoke `mcp_-_datasearch` on the `glean` MCP server with the constructed message.
Strip trailing metadata (`chatId`, `traceId`, `workflowRunId`) from the response.

---

## Step 3: Extract cited documents

From the response, extract all documents referenced. For each, capture:
- Title
- URL (if present)
- A one-sentence summary of what it contributes to answering the question

---

## Step 4: Assess confidence

| Condition | Confidence |
|---|---|
| 3+ on-topic documents cited, question clearly answered | HIGH |
| 1–2 documents cited, or answer is partial | MEDIUM |
| No documents found, or results are off-topic | LOW |

---

## Step 5: Deliver the evidence block

Format the evidence block using `OUTPUT-FORMAT.md`.

**If output destination is "Inline only":** Print the full evidence block in the conversation.

**If output destination is "Save to file":**
1. Write the full evidence block to `products/{product}/document-lookups/{filename}.md`
   (create the directory if it does not exist).
2. Print only a short inline summary:

```
Saved: products/{product}/document-lookups/{filename}.md
Documents cited: {n}  Confidence: {HIGH | MEDIUM | LOW}
Question: {question}
```
