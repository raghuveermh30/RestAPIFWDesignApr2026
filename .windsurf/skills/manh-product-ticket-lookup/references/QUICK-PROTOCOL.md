# Quick Protocol

Returns a JSON list of matching tickets with summary fields. Use when you want to
scan, count, or build a signal inventory — or as the first step before a Deep lookup.

---

## Execution

### Step 1: Build the query message

Construct a single concise paragraph — no bullet points, no newlines inside the
message. Multiline messages cause connection timeouts.

```
Search JIRA project {project-key} for tickets about {topic}{if issue-types: , focusing on {issue-types}}{if time-range: , restricted to {time-range}}{if status: , with status {status}}. Return a JSON array where each item has: ticket_id, url, type, summary, status, key_finding (one sentence on what the ticket means as a signal). No prose, JSON only.
```

### Step 2: Call the ticket search tool

Invoke `mcp_-_datasearch` on the `glean` MCP server with the constructed message.

### Step 3: Parse and normalize the response

Extract the JSON array from the response. Strip any trailing Glean metadata
(`chatId`, `traceId`, `workflowRunId`).

Normalize the `type` field to one of: `Bug`, `Story`, `CII`, `Product Request`,
`Enhancement`, `Ticket`. Map ambiguous values (e.g. `CII/enhancement`) to the
dominant type.

### Step 4: Derive key finding

Synthesize a 1–3 sentence Key Finding across the full result set:
- How many tickets were found
- What type distribution looks like (e.g. "mostly CIIs and product requests")
- What the dominant pattern or gap is across the results

### Step 5: Assess confidence

| Condition | Confidence |
|---|---|
| 5+ tickets returned, clearly on-topic | HIGH |
| 2–4 tickets returned, or partially on-topic | MEDIUM |
| 0–1 tickets returned, or off-topic results | LOW |

### Step 6: Deliver the evidence block

Format the evidence block using the output template below.

**If output destination is "Inline only":** Print the full evidence block in
the conversation.

**If output destination is "Save to file":**
1. Write the full evidence block to `products/{product}/ticket-lookups/{filename}.md`
   (create the directory if it does not exist).
2. Print only a short inline summary:

```
Saved: products/{product}/ticket-lookups/{filename}.md
Tickets found: {n}  Confidence: {HIGH | MEDIUM | LOW}
Topic: {topic}  Project: {project-key}
```

---

## Output block

```markdown
## Ticket Lookup: "{topic}"
Mode: Quick
Product: {product}  Project: {project-key}
Filters: {issue-types | "all types"} | {time-range | "no time restriction"} | {status | "all statuses"}
Searched: {YYYY-MM-DD}

### Results ({n} tickets)
| ID | Type | Summary | Status | URL |
|---|---|---|---|---|
| {ticket-id} | {type} | {summary} | {status} | {url} |

### Key Finding
{1–3 sentences: what this list tells you as a signal}

Confidence: {HIGH | MEDIUM | LOW}
```
