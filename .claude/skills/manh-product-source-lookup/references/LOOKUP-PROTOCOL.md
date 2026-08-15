# Source Lookup Protocol

Search strategies used by `harness-source-lookup` agent, keyed by question type.

---

## Type: `entity-field`

Looking for a field or attribute on a domain entity.

```bash
# EF entity XML (primary — Manhattan entity definitions live here)
grep -rn "{term}" {repo}/src/main/resources/entity/ --include="*.xml"

# JPA entity class (fallback)
grep -rn "{term}" {repo}/src/main/java/ --include="*.java" | grep -i "@Column\|@Entity\|@Table"
```

Use 2-3 candidate terms (e.g. for "uom field on fulfillmentLine" → `uom`, `unitOfMeasure`, `orderingUom`).

---

## Type: `api-shape`

Looking for a REST endpoint shape or DTO field.

```bash
# Controller mapping + term co-occurrence
grep -rn "{term}" {repo}/src/main/java/ --include="*.java" | grep -i "Mapping\|Controller"

# DTO / request / response class
grep -rn "{term}" {repo}/src/main/java/ --include="*.java" | grep -i "dto\|request\|response\|payload"
```

---

## Type: `config-key`

Looking for a business configuration entry.

```bash
# Step A — product-level catalog (fast, no source needed)
grep -i "{term}" {harness-lib}/products/{product}/business-config-catalog.md

# Step B — ConfigStore usages in source (if Step A misses)
grep -rn "{term}" {repo}/src/main/java/ --include="*.java" | grep -i "ConfigStore\|getConfig\|configKey"
```

---

## Type: `feature-flag`

Looking for a feature flag definition or usage.

```bash
# Flag usages
grep -rn "features\.isOn\|isFeatureOn" {repo}/src/main/java/ --include="*.java" | grep -i "{term}"

# Flag definitions
grep -rn "{term}" {repo}/src/main/resources/ --include="*.xml" | grep -i "feature\|flag"
```

---

## Type: `general`

Broad keyword search when no specific type matches.

```bash
grep -rni "{term}" {repo}/src/main/java/ --include="*.java" --include="*.xml" --include="*.yml"
```

Use 2-3 distinctive terms from the question. Avoid stop words.

---

## Session workspace record

When this skill is first triggered within a `manh-product-explore` or
`manh-product-req-expand` session, add this block to the interview/requirements file
and update it on each subsequent lookup:

```markdown
## Source Lookup Context
Workspace: {resolved workspace path}
Product: {product}
Repos accessed this session:
| repo | path | status |
|---|---|---|
| {repo-name} | {local-path} | cloned --depth=1 \| cache hit \| unavailable |
```
