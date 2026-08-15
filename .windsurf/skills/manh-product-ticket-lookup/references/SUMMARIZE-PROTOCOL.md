# Summarize Protocol

Returns a narrative synthesis across JIRA tickets for the given topic. Glean groups
results by theme, surfaces workarounds and design decisions, and cross-references
related tickets. Use when you want to understand the landscape before forming
specific questions.

---

## Execution

### Step 1: Build the query message

Construct a single concise paragraph — no bullet points, no newlines inside the
message. Multiline messages cause connection timeouts.

```
Search JIRA project {project-key} for tickets about {topic}{if issue-types: , focusing on {issue-types}}{if time-range: , restricted to {time-range}}{if status: , with status {status}}. Group results by theme and summarize patterns, workarounds, design decisions, and recurring gaps. Note which tickets are customer-committed. No JSON.
```

### Step 2: Call the ticket search tool

Invoke `mcp_-_datasearch` on the `glean` MCP server with the constructed message.

### Step 3: Extract cited ticket IDs

From the narrative response, extract all ticket IDs referenced (pattern: `{PROJECT-KEY}-{number}`,
e.g. `SIF-130592`). Build a table of cited tickets.

### Step 4: Assess confidence

| Condition | Confidence |
|---|---|
| 5+ tickets referenced, clearly on-topic | HIGH |
| 2–4 tickets referenced, or partially on-topic | MEDIUM |
| 0–1 tickets referenced, or off-topic results | LOW |

### Step 5: Deliver the evidence block

Format the evidence block using the output template below. Strip any trailing
Glean metadata (`chatId`, `traceId`, `workflowRunId`) before delivering.

**If output destination is "Inline only":** Print the full evidence block in
the conversation.

**If output destination is "Save to file":**
1. Write the full evidence block to `products/{product}/ticket-lookups/{filename}.md`
   (create the directory if it does not exist).
2. Print only a short inline summary:

```
Saved: products/{product}/ticket-lookups/{filename}.md
Tickets referenced: {n}  Confidence: {HIGH | MEDIUM | LOW}
Topic: {topic}  Project: {project-key}
```

---

## Output block

```markdown
## Ticket Lookup: "{topic}"
Mode: Summarize
Product: {product}  Project: {project-key}
Searched: {YYYY-MM-DD}

### Signal Summary
{Glean's synthesized narrative — paste verbatim, preserving theme groupings}

### Tickets Referenced
| ID | Type | Summary | Status |
|---|---|---|---|
| {ticket-id} | {type} | {summary} | {status} |

Confidence: {HIGH | MEDIUM | LOW}
```
