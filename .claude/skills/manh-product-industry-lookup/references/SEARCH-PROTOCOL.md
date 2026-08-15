# Industry Search Protocol

Executes external research for a given topic using web search. Returns a structured
evidence block per `OUTPUT-FORMAT.md`.

---

## Backend

Use the `web_search` tool for all queries. Run 2–3 targeted searches per lookup —
broad first, then narrowed by finding. Fetch the most relevant page with `webfetch`
when a search result title suggests high-value content (e.g. a vendor whitepaper,
standards body page, or analyst report).

---

## Step 1: Infer domain context

If `domain` is not provided, infer it from the product slug:

| Product slug | Default domain |
|---|---|
| `sif` | store fulfillment |
| `wms` | warehouse management |
| `matm` | transportation management |
| `pos` | point of sale |
| Unknown | supply chain |

---

## Step 2: Build search queries

Construct 2–3 queries based on the confirmed query type. Keep queries concise and
specific — avoid generic terms that surface marketing content.

### Industry standard

```
Query 1: {domain} {topic} industry standard best practice
Query 2: {topic} {domain} WMS OR "supply chain" standard approach site:gartner.com OR site:forrester.com OR site:mckinsey.com
Query 3 (if needed): {topic} {domain} specification OR standard OR guideline
```

### Competitive signal

```
Query 1: {topic} {domain} SAP OR "Blue Yonder" OR Oracle OR "Manhattan Associates" how
Query 2: {topic} {domain} ERP WMS comparison feature
Query 3 (if needed): {topic} site:sap.com OR site:blueyonder.com OR site:oracle.com
```

### Market trend

```
Query 1: {topic} {domain} trend 2024 OR 2025
Query 2: {topic} supply chain emerging technology direction
Query 3 (if needed): {topic} {domain} forecast analyst
```

### Broad external

```
Query 1: {topic} {domain} best practice OR standard OR approach
Query 2: {topic} supply chain industry
```

---

## Step 3: Execute searches and fetch top results

For each query, run `web_search`. From the results:
1. Identify the 3–5 most relevant results by title and summary
2. For high-value pages (whitepapers, standards docs, analyst pieces), run `webfetch`
   to extract the relevant section — do not fetch marketing/landing pages
3. Discard results that are vendor marketing, job postings, or press releases

---

## Step 4: Synthesize findings

From all fetched content, synthesize:
- What the industry consensus is (if one exists)
- Where there is variation or debate
- How this compares to what Manhattan's product currently does (if known from context)
- Any notable gaps or opportunities this signals

---

## Step 5: Assess confidence

| Condition | Confidence |
|---|---|
| 3+ authoritative sources agree on a clear standard or trend | HIGH |
| 2 sources found, or sources partially address the topic | MEDIUM |
| Only 1 source, or results are marketing-heavy / off-topic | LOW |

Authoritative sources: standards bodies, analyst firms (Gartner, Forrester, McKinsey),
peer-reviewed publications, vendor technical documentation (not marketing).

---

## Step 6: Deliver the evidence block

Format the evidence block using `OUTPUT-FORMAT.md`.

**If output destination is "Inline only":** Print the full evidence block in the conversation.

**If output destination is "Save to file":**
1. Write the full evidence block to `products/{product}/industry-lookups/{filename}.md`
   (create the directory if it does not exist).
2. Print only a short inline summary:

```
Saved: products/{product}/industry-lookups/{filename}.md
Sources cited: {n}  Confidence: {HIGH | MEDIUM | LOW}
Topic: {topic}
```
