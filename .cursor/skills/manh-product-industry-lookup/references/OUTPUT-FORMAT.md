# Industry Lookup Output Format

Evidence block template for `manh-product-industry-lookup`. Used for both inline
delivery and file output.

---

## Evidence block

```markdown
## Industry Lookup: "{topic}"
Type: {Industry standard | Competitive signal | Market trend | Broad external}
Domain: {domain}  Product context: {product}
Searched: {YYYY-MM-DD}

### Findings
{Synthesized narrative — what the industry says, where there is consensus vs debate,
 how it relates to Manhattan's product context. Written in prose, not bullets.}

### Sources Cited
| Title | Publisher | Summary | URL |
|---|---|---|---|
| {title} | {publisher / site} | {one-sentence summary of what this source contributes} | {url} |

### Implications for {product}
{1–3 sentences: what this external signal means specifically for the product area.
 Does it validate current direction? Surface a gap? Suggest a design pattern to adopt?}

### Gaps
{What this search could not answer — missing authoritative sources, topics requiring
 deeper domain expertise, or questions better answered by an internal lookup.
 Write "None" if fully answered.}

Confidence: {HIGH | MEDIUM | LOW}
```

---

## Confidence rules

| Condition | Confidence |
|---|---|
| 3+ authoritative sources agree on a clear standard or trend | HIGH |
| 2 sources found, or sources partially address the topic | MEDIUM |
| Only 1 source, or results are marketing-heavy / off-topic | LOW |

---

## Implications field guidance

This field is what differentiates industry-lookup from a raw web search. It grounds
the external signal in the product context so `manh-product-research` Phase 4
synthesis can directly use it.

Good: "The industry consensus on dual-UOM persistence (ordering qty + catch-weight qty stored separately) validates SIF's current design direction in SIF-130592. The tolerance window pattern (±10%) is also standard — no gap here."
Bad: "This is relevant to the product."

## Gaps field guidance

The Gaps field feeds `manh-product-research` Phase 4. Be specific about what's missing.

Good: "Could not find a clear industry standard for carrier UOM localization (LB vs KG per country) — Gartner or a UPS/FedEx technical spec would be the right next source."
Bad: "More research may be needed."
