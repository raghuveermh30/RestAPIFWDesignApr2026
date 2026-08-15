# Phase 2 — optimize the scenario set (apply in order)

1. **Merge equivalents**: if two one-liners assert the same behavior and differ only by a value that does not affect the outcome, merge; keep both combination_ids under `covers[]`.
2. **Merge observation-layer duplicates (MANDATORY)**: if two one-liners assert the **same guarantee observed at different layers or with different instruments** — e.g. one checks the frontend (browser Network tab shows no duplicate request) and another checks the backend (API log / DB shows ≤1 order created) for the same action — merge into a **single scenario whose one-liner names both observation points** (e.g. "Verify a rapid double-click dispatches at most one performOrderReturn — confirmed at both the client request layer and the backend"). Keep both combination_ids under `covers[]`. Do not emit one scenario per layer; stage 4 turns the single scenario into one case asserting at each layer. The same applies when two one-liners differ only in polarity label (Negative vs Boundary) while asserting the identical guarantee — merge and keep the stronger polarity.
3. **Split overloaded combos**: if one combination implies two distinct observable behaviors, split into two one-liners (each keeps the id with a suffix, e.g. C007a / C007b).
4. **Add plan-implied scenarios**: add one-liners for behaviors the plan's Acceptance Criteria / Risks require that no combination expresses (error messaging, idempotency, audit/logging). Tag `source: plan-derived`.
5. **Prune duplicates / unreachable**; record under `dropped[]`.
6. **Order**: Positive first, then Negative, then Boundary (matches the team's test-script convention).

## Behavioral scenario rules

Combinations in `behavioral_scenarios[]` (ids prefixed `BS`) are **targeted scenario tests** — they intentionally reuse parameter vectors from matrix rows but cover distinct observable behaviors (UI layout, localization, lifecycle, crash/relaunch, mid-flow state changes). Apply these rules:

- **Never deduplicate** a behavioral scenario against a matrix row, even if all nine parameter values match.
- **Always retain all behavioral scenarios**; dropping one is equivalent to dropping a plan acceptance criterion.
- When a behavioral scenario's parameter vector is contradictory (e.g. Apply disabled when the label describes an in-flight apply), **reseed the parameter vector to match the label** — the label is authoritative.
- One-liners for behavioral scenarios are derived from the label, not from the parameter vector.

## Worked example — observation-layer merge (generic)

Candidate one-liners:
- S009: "No duplicate performOrderReturn fires on rapid double-click — verified via the browser **Network tab** (frontend)."
- S011: "Only one performOrderReturn request reaches the **backend** — verified via the server API log."

Both assert the **same guarantee** (≤1 performOrderReturn for one rapid-click interaction); they differ only by observation layer.

Merge → one scenario: "Rapid double-click results in **at most one performOrderReturn**, verified at **both the frontend (Network tab) and the backend (API log)**." `covers[]` = union of both ids.

Keep separate only when the layers assert *different* guarantees (e.g. frontend asserts the button is disabled; backend asserts idempotency of a retried request).

Counter-example (do NOT merge): two Boundary scenarios that assert distinct behaviors — preserve both even if they share parameter values.
