# Orientation Brief Template

Used exclusively by `harness-research-synthesizer`. Do not load in the orchestrator.

---

## File location

`products/{product}/research/{slug}-{date}-orientation.md`

> **MANDATORY — all models**: Slug comes first, date second. Check for an existing file
> at this path before writing. If it exists, append `-2`, `-3`, etc.:
> `products/{product}/research/{slug}-{date}-orientation-2.md`
> Never overwrite an existing brief silently.

---

## Template

```markdown
# Orientation Brief: {idea}

**Product**: {product}
**Mode**: {Quick | Deep}
**Date**: {date}
**Layers run**: {comma-separated list of layers that completed}
**Status**: Ready for explore

---

## What we think this is about

{1-2 paragraphs. Restate the vague idea in product terms — what capability area it
likely touches, what business problem it is trying to solve, and how it relates to
the existing product landscape. This is a working hypothesis, not a conclusion.
Ground it in the grounding-facts and the evidence layers. Be honest about ambiguity.}

---

## Layers swept

| Layer | Queries | Confidence | Verdict | Key finding |
|---|---|---|---|---|
| Tickets | {N} | {HIGH\|MEDIUM\|LOW} | {FOUND\|PARTIAL\|NOT FOUND} | {one line} |
| Documents | {N} | {HIGH\|MEDIUM\|LOW} | {FOUND\|PARTIAL\|NOT FOUND} | {one line} |
| Source | {N} | {HIGH\|MEDIUM\|LOW} | {FOUND\|PARTIAL\|NOT FOUND} | {one line} |
| Industry | {N} | {HIGH\|MEDIUM\|LOW} | {FOUND\|PARTIAL\|NOT FOUND} | {one line} |

*(Omit rows for layers not run.)*

---

## What already exists in the product

*Source layer findings. Prevents the explore session from re-discovering built things.*

{If source layer ran and found things:}
- **{ClassName / module name}** (`{file path or package}`): {one sentence — what it does
  and how it relates to the idea}
- {repeat for each relevant finding}

{If source layer ran and found nothing:}
> No existing implementation found. The codebase does not appear to have anything
> directly related to this idea. See Gaps section.

{If source layer was not run:}
> Source layer not swept in this session (Quick mode — idea type: {type}).

---

## What customers have said

*Ticket layer findings. Grounds the explore session in real user pain.*

{If tickets found:}
- **{ticket count} tickets** found across {date range}
- Top themes:
  - {theme 1 with representative ticket IDs if available}
  - {theme 2}
  - {theme 3}
- Notable patterns: {workarounds reported, severity signals, recurrence frequency}

{If no tickets found:}
> No tickets found on this topic. Either the problem has not been formally reported,
> or different terminology is used internally. See Gaps.

{If ticket layer was not run:}
> Ticket layer not swept in this session.

---

## What has been decided before

*Document layer findings. Prevents the explore session from re-debating settled questions.*

{If documents found:}
- **{document title}** ({date}): {one sentence — key decision or context}
  - Decision: {what was decided}
  - Rejected alternatives: {if any}
- {repeat for each relevant document}

{If no documents found:}
> No prior decisions or design documents found on this topic. Either this has not been
> formally explored before, or documentation lives outside indexed sources.

{If document layer was not run:}
> Document layer not swept in this session.

---

## What the industry does

*Industry layer findings. Gives the explore session external benchmarks.*

{If industry layer ran and found things:}
- **{standard / vendor / approach}**: {one sentence — what they do and how it relates}
  - Source: {URL or document name}
- {repeat for each finding}
- **Industry direction**: {one sentence on where the industry is heading on this topic}

{If industry layer ran and found nothing:}
> No authoritative industry standards or competitive signals found on this specific topic.
> The problem may be domain-specific to Manhattan's customer segment.

{If industry layer was not run:}
> Industry layer not swept (Quick mode — recommended for {idea-type} classification).
> Run in Deep mode to add external benchmarks.

---

## Known gaps

*What no layer found. These become the first questions for the explore session.*

{List every significant unknown that the sweep did not answer. Be specific.}

- {Gap 1 — e.g. "No source found for UOM conversion in the outbound flow. Unknown if
  sif-fulfillment handles this or delegates to sif-core."}
- {Gap 2}
- {Gap 3}
[- {Gap 4}]
[- {Gap 5}]

If no gaps: state "All major orientational unknowns were resolved by the sweep."
This is rare — be honest.

---

## Seeded questions for the explore session

*3-5 specific, evidence-grounded questions derived from the gaps and signals above.
Ready to open the explore session with. Each question references what was found
(or not found) to give the explore session a concrete starting point.*

1. **{Question 1}**
   *Grounded in*: {which layer finding or gap prompted this question}

2. **{Question 2}**
   *Grounded in*: {which layer finding or gap prompted this question}

3. **{Question 3}**
   *Grounded in*: {which layer finding or gap prompted this question}

[4. **{Question 4}**
   *Grounded in*: {which layer finding or gap prompted this question}]

[5. **{Question 5}**
   *Grounded in*: {which layer finding or gap prompted this question}]

---

## Recommended next step

```
/manh-product-explore

Pre-read: Share this brief with the PM, BA, and Architect before the explore session.
The seeded questions above are the recommended opening questions.
The explore session should NOT re-run any of the sweeps done here —
use the cache files directly if source-level evidence is needed during the interview.
```

Cache files available at: `products/{product}/research/.cache/`

---

## Related artifacts

- {Layer cache files — list each that was written}
- {Any prior explore docs found in document layer — link if found}
```

---

## Synthesis guidelines (for `harness-research-synthesizer`)

1. **Read cache files, not compact blocks** — the compact blocks give you confidence
   and verdicts; the cache files give you the actual evidence to synthesize from.
   Load each cache file fully before writing the corresponding section.

2. **Hypothesis first** — the "What we think this is about" section is written last
   but placed first. Write it after synthesizing all layers so it reflects the full picture.

3. **Gaps are the most important section** — an unexplored area is more valuable for
   the explore session than a list of findings. Name every significant unknown precisely.

4. **Seeded questions must be actionable** — each question must be answerable by a PM,
   BA, or Architect in an explore session. Avoid technical questions — those belong in
   the technical clusters of explore, not the opening.

5. **Do not editorialize** — in the evidence sections (what exists, what customers said,
   what was decided, what industry does), report what was found. Save analysis for
   the hypothesis section and seeded questions.

6. **Omit empty sections cleanly** — if a layer was not run, say so in one line.
   Do not leave blank sections or placeholder text.
