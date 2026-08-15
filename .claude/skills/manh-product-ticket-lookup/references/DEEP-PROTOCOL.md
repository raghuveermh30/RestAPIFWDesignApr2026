# Deep Protocol

Returns full detail on all matching tickets — description, comments, and custom
fields. Runs in two phases: first a Quick call to get the complete list, then
paginated detail calls in batches of 5 until all tickets are covered. Use when
you need raw pain statements, workaround details, or comment thread history across
the full result set.

---

## Execution

### Step 1: Get the full ticket list

Run the Quick protocol (`references/QUICK-PROTOCOL.md`) with the same inputs to
get the complete list of matching tickets. Record the total count — this is how
many detail calls will be made across all batches.

### Step 2: Paginate in batches of 5

Divide the list into sequential batches of 5 tickets each.

```
Batch 1: tickets 1–5
Batch 2: tickets 6–10
Batch 3: tickets 11–15
...
```

Process all batches. Do not stop early.

### Step 3: Fetch full detail — one call per ticket, batch by batch

For each ticket in the current batch, construct and send this message as a single
line — no newlines inside the message. Multiline messages cause connection timeouts.

```
Fetch full details of JIRA ticket {ticket-id} as JSON with fields: ticket_id, key, url, type, summary, status, priority, reporter, assignee, created_date, updated_date, resolution, labels, components, full description text, all comments with author and timestamp, linked_tickets, attachments, and custom fields: customercommitted, initiatedby, productmodule, department, defectcategory.
```

Invoke `mcp_-_datasearch` on the `glean` MCP server. Strip trailing Glean
metadata (`chatId`, `traceId`, `workflowRunId`) from each response.

Run calls within a batch sequentially. If a call fails, mark that ticket as
`unavailable` and continue. After completing a batch, proceed immediately to
the next batch without pausing.

### Step 4: Extract meaningful fields per ticket

From each full detail response, extract:

| Field | Source |
|---|---|
| `ticket_id`, `url`, `type`, `summary`, `status`, `priority` | Core fields |
| `reporter`, `created_date`, `updated_date` | Core fields |
| `full_description` | `description` or `full_description` field |
| `comments` | `comments` array — keep author, timestamp, text |
| `customercommitted` | `custom_fields.customercommitted` |
| `initiatedby` | `custom_fields.initiatedby` |
| `productmodule` | `custom_fields.productmodule` |
| `department` | `custom_fields.department` |
| `defectcategory` | `custom_fields.defectcategory` |
| `linked_tickets` | `linked_tickets` array |

Discard all other custom fields (internal metadata, template fields, tracking IDs).

Truncate `full_description` to 600 characters if longer. Append `… (see {url} for full text)`.

### Step 5: Derive signal pattern

After all batches are complete, synthesize a Signal Pattern across all fetched
tickets (2–4 sentences):
- What recurring theme, gap, or constraint emerges
- Whether workarounds exist and what form they take
- Whether the pattern points to a product gap, a design decision, or a
  customer-specific issue

### Step 6: Assess confidence

| Condition | Confidence |
|---|---|
| 3+ tickets successfully fetched, clearly on-topic | HIGH |
| 1–2 tickets fetched, or partially on-topic | MEDIUM |
| All fetches failed or off-topic | LOW |

### Step 7: Deliver the evidence block

Format the evidence block using the output template below.

**If output destination is "Inline only":** Print the full evidence block in
the conversation.

**If output destination is "Save to file":**
1. Write the full evidence block to `products/{product}/ticket-lookups/{filename}.md`
   (create the directory if it does not exist).
2. Print only a short inline summary:

```
Saved: products/{product}/ticket-lookups/{filename}.md
Tickets fetched: {n-successfully-fetched} of {total-in-list}  Confidence: {HIGH | MEDIUM | LOW}
Topic: {topic}  Project: {project-key}
```

---

## Output block

```markdown
## Ticket Lookup: "{topic}"
Mode: Deep
Product: {product}  Project: {project-key}
Total tickets: {total-in-list}  Fetched: {n-successfully-fetched}  Unavailable: {n-failed}
Searched: {YYYY-MM-DD}

---

### {ticket-id-1} — {summary}
Type: {type}  Status: {status}  Priority: {priority}
Reporter: {reporter}  Created: {created_date}
Customer Committed: {yes/no/—}  Initiated By: {initiatedby}  Module: {productmodule}

**Pain Statement**
{full_description — truncated to 600 chars if long, with link to full text}

**Comments**
- {author} ({timestamp}): {comment text}
- {author} ({timestamp}): {comment text}

**Linked Tickets**
{linked_tickets list, or "None"}

URL: {url}

---

### {ticket-id-2} — {summary}
...

---

### Signal Pattern
{2–4 sentences: recurring theme, workarounds, gap classification}

Confidence: {HIGH | MEDIUM | LOW}
```
