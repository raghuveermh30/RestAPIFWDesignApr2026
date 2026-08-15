# manh-workbench-analyzer

Sub-analyzer invoked by `manh-mup-repo-analyzer` **Layer 10** when `ui-workbench` is
detected in `mup.config.prod.json`'s `deps` object. Clones `ui-workbench` and
`component-ai-workbench`, analyzes both, and generates the
`manh-ui-workbench-framework` skill in the target repo.

---

## Sub-step W.1: Clone Repos

For each repo, check for an existing local copy before cloning.

### ui-workbench

**Check order** (stop at first match containing `src/`):

1. `{target-repo-parent}/ui-workbench/`
2. `{target-repo-parent}/mup-analyzer-dependent-repos/ui-workbench/`

If neither exists, clone using the `origin` stored in `{workbench-origin}` (from Step 0.5):

```bash
git clone git@{workbench-origin.host}:{workbench-origin.repoOwner}/{workbench-origin.repo}.git \
  {target-repo-parent}/mup-analyzer-dependent-repos/ui-workbench
```

Store resolved path as `{ui-workbench-path}`.

### component-ai-workbench

**Check order** (stop at first match containing `src/main/`):

1. `{target-repo-parent}/component-ai-workbench/`
2. `{target-repo-parent}/mup-analyzer-dependent-repos/component-ai-workbench/`

If neither exists, clone from the fixed Bitbucket URL:

```bash
git clone git@bitbucket.org:manhattanassociates/component-ai-workbench.git \
  {target-repo-parent}/mup-analyzer-dependent-repos/component-ai-workbench
```

Store resolved path as `{ai-workbench-path}`.

### Failure handling

On clone failure for either repo, halt with:

```
✗ Clone failed for {repo-name}: {error}
  Ensure SSH access to bitbucket.org is configured and retry.
  You can also place the repo manually at:
    {target-repo-parent}/{repo-name}/
  and re-run Layer 10.
```

---

## Sub-step W.2: Analyze `ui-workbench`

Run all sections below from `{ui-workbench-path}/src/lib/`.

### A. Angular Library Catalog

Scan each subdirectory and extract the following:

| Category | Directory | What to extract |
|----------|-----------|----------------|
| Angular Components | `components/ng-components/`, `components/ui-components/` | Class name, selector, `@Input`/`@Output` summary, purpose |
| Widgets | `widgets/` | Widget class name, base class, key lifecycle hooks, configurable properties |
| Module | `modules/workbench.module.ts` | Declared symbols, exported symbols, required module imports |
| Services | `services/` | Service class name, key public methods, what it manages |
| Schemas | `schemas/` | Schema type/interface name, key fields, what it describes |
| Pages | `pages/` | Page component names and their route contexts |
| Models | `models/` | Interface/class names and key fields |
| Events | `events/` | Event class names and their payloads |
| Context Providers | `context-providers/` | Provider names and what context they inject |
| Workbench Host | `workbench-host/` | Host component selector and purpose |

For each item: record name, file path (relative to `{ui-workbench-path}`), and a 1-line
description inferred from class name, Javadoc/JSDoc, or constructor signature.

### B. Public API (exported symbols)

Read `{ui-workbench-path}/src/index.ts`. Collect all exported symbols grouped by the
subdirectory re-export they originate from (e.g., `components`, `widgets`, `services`).
This is the definitive list of what consuming products can import.

### C. Usage Patterns in Target Repo and Products

Grep the target repo and all product repos for imports of the workbench package:

```bash
grep -rn "@ma-iris/ui-workbench\|ui-workbench" {product-repo-path}/ \
  --include="*.ts" -l
```

For each file found:
- Which exported symbols are imported
- How they are used: module import, component instantiation, service injection,
  widget extension (class extending `WorkbenchWidget`), schema definition

Build a usage table per product:

| File | Imported Symbol | How Used |
|------|----------------|---------|
| `[file path]` | `[ClassName]` | `[module import / component / service injection / widget extension]` |

### D. Workbench Metadata Schemas in `ui-metadata`

Scan the framework metadata repo and all product metadata paths for workbench schema files:

```bash
# In fw-ui-metadata repo (already available from {fw-repo-paths})
find {fw-ui-metadata-path}/ -name "*.json" | xargs grep -l -i "workbench" 2>/dev/null
find {fw-ui-metadata-path}/ -path "*/workbenches/*" -name "*.json"

# In each product repo
find {product-repo-path}/ -path "*/workbenches/*" -name "*.json"
find {product-repo-path}/ -name "*.json" | xargs grep -l -i "workbench" 2>/dev/null
```

For each schema file found, record:
- File path (relative to repo root)
- Top-level `type` field value (e.g., `WORKBENCH`)
- Key structural fields (widget definitions, datasource configs, layout sections)
- Which product or feature uses this schema

---

## Sub-step W.3: Analyze `component-ai-workbench`

### A. Native REST Endpoints

Find all Spring `@RestController` classes:

```bash
grep -rn "@RestController" {ai-workbench-path}/src/main/java/ --include="*.java" -l
```

For each controller file:
1. Read the class-level `@RequestMapping` value to get the **base path**
2. For each method annotated with `@GetMapping`, `@PostMapping`, `@PutMapping`,
   `@DeleteMapping`, or `@PatchMapping`:
   - **HTTP method**: infer from annotation name
   - **Full path**: base path + method-level mapping value
   - **Request body**: parameter annotated with `@RequestBody` (class name only)
   - **Return type**: method return type (unwrap `ResponseEntity<T>` → `T`)
   - **Description**: first sentence of Javadoc `/** */` if present; otherwise derive
     from the method name
3. **Group by domain**: use the Java package sub-path below the component root package

### B. Command Router Entries

Read `{ai-workbench-path}/src/main/resources/component.properties`.

Find the facade prefix:
```
manh.command-router.prefix=/api/ai-workbench
```

Parse every `{commandKey}` triplet:
```
manh.command-router.commands.{commandKey}.commandPath=...
manh.command-router.commands.{commandKey}.targetComponent=...
manh.command-router.commands.{commandKey}.targetEndpoint=...
```

Group entries by `targetComponent` in the output table.

### C. UI Usage Cross-Reference

Grep the target repo and all product repos for HTTP calls matching the
`component-ai-workbench` URL prefix:

```bash
grep -rn "ai-workbench\|workbench" {product-repo-path}/ --include="*.ts" | grep -i "/api/"
```

For each match: file path, inferred HTTP method (`http.get` → GET, etc.), full endpoint
called, and domain grouping.

---

## Sub-step W.4: Generate `ui-workbench.md`

**Output path**:
```
{target-repo}/.manh-ai-harness/skills/manh-ui-workbench-framework/references/ui-workbench.md
```

Use this template (replace all bracketed placeholders with findings from W.2):

````markdown
# Reference: ui-workbench — Angular Workbench Library

> Source repo: `ui-workbench` (`@ma-iris/ui-workbench`)
> Generated by: manh-workbench-analyzer

## Overview

`ui-workbench` is the Angular library providing the workbench framework for MUP applications.
It provides the host container, widget infrastructure, schema definitions, and backend
integration services for building configurable, data-driven workbench pages.

## Angular Components

| Component | Selector | Key Inputs | Key Outputs | Purpose |
|-----------|---------|-----------|------------|---------|
| [ComponentClass] | `[selector]` | [inputs] | [outputs] | [purpose] |

## Widgets

| Widget Class | Base Class | Key Properties | Purpose |
|-------------|-----------|--------------|---------|
| [WidgetClass] | [BaseClass] | [configurable fields] | [purpose] |

## Module: WorkbenchModule

| Symbol Type | Name | Purpose |
|-------------|------|---------|
| Declared | [ComponentClass] | [purpose] |
| Exported | [ComponentClass] | [purpose] |
| Requires | [ImportedModule] | [purpose] |

## Services

| Service | Key Methods | Manages |
|---------|------------|---------|
| [ServiceClass] | `[methodName(args): ReturnType]` | [what state or resource] |

## Schemas

| Schema Type | Key Fields | What it Defines |
|-------------|-----------|----------------|
| [SchemaClass/Interface] | [field names] | [what it describes] |

## Domain Models

| Interface / Class | Key Fields | Purpose |
|------------------|-----------|---------|
| [ModelClass] | [fields] | [what it represents] |

## Events

| Event Class | Payload | When Emitted |
|-------------|---------|-------------|
| [EventClass] | [fields] | [trigger] |

## Context Providers

| Provider | Injected Context | How to Use |
|----------|-----------------|-----------|
| [ProviderName] | [what it provides] | [injection pattern] |

## Workbench Host

[Description of the host component, its selector, and how it renders the workbench page.]

## Public API Summary

All symbols exported from `src/index.ts`, grouped by category:

| Category | Exported Symbols |
|----------|----------------|
| Components | [names] |
| Widgets | [names] |
| Module | [names] |
| Services | [names] |
| Schemas | [names] |
| Models | [names] |
| Events | [names] |
| Context Providers | [names] |

## Integration Patterns

### Importing the Module

```typescript
// Add to your Angular module or standalone imports:
import { WorkbenchModule } from '@ma-iris/ui-workbench';
```

[Describe any required providers, tokens, or configuration that must accompany the import.]

### Extending a Widget

```typescript
import { WorkbenchWidget } from '@ma-iris/ui-workbench';

export class MyCustomWidget extends WorkbenchWidget {
  // [describe which lifecycle hooks to implement and what properties to configure]
}
```

### Using Context Providers

[Describe how context providers are registered (e.g., in the module `providers` array) and
how they are injected into widgets or components.]

### Registering with the Workbench Registry

[If the workbench uses a registry pattern (similar to MUP's component registry), describe
how widgets or components are registered so the host can resolve them at runtime.]

## Workbench Metadata Schemas

Workbench pages are configured via JSON metadata files discovered in `ui-metadata`.

| Schema File | Type | Key Fields | Used By |
|-------------|------|-----------|---------|
| `[path relative to repo root]` | `[type value]` | [widget defs, datasource, layout] | [product / feature] |
````

---

## Sub-step W.5: Generate `component-ai-workbench.md`

**Output path**:
```
{target-repo}/.manh-ai-harness/skills/manh-ui-workbench-framework/references/component-ai-workbench.md
```

Use this template (replace all bracketed placeholders with findings from W.3):

````markdown
# Reference: component-ai-workbench — Workbench Backend Facade

> Source repo: `component-ai-workbench`
> Facade API prefix: `/api/ai-workbench`
> Generated by: manh-workbench-analyzer

## Overview

`component-ai-workbench` is the backend facade microservice for the workbench framework.
It exposes **native REST APIs** for workbench data operations, and **routes UI calls** to
other backend components via the Command Router.

## Native REST Endpoints

[For each domain group found in the Java source, generate a section:]

### {Domain} Endpoints

| HTTP Method | Path | Request Body | Response | Description |
|-------------|------|-------------|----------|-------------|
| [GET/POST/…] | `/api/ai-workbench/[path]` | [BodyClass or —] | [ResponseType] | [description] |

## Command Router Entries

Facade prefix: `/api/ai-workbench`

[For each `targetComponent`, generate a section:]

### Routed to: `{targetComponent}`

| Command Key | UI Calls (facade path) | Forwarded To |
|-------------|----------------------|--------------|
| [commandKey] | `/[commandPath]` | `[targetEndpoint]` |

## UI Usage in [{target-repo-name}]

Endpoints actually called by this application's product repos:

| UI File | HTTP Method | Endpoint | Domain |
|---------|------------|---------|--------|
| `[file path]` | [GET/POST/…] | `/api/ai-workbench/[path]` | [domain] |

## Domain Knowledge

[For each domain grouping found, write 2–4 sentences explaining the business purpose:
"**[Domain]**: Endpoints in this domain …"]
````

---

## Sub-step W.6: Generate Reference Files and Update App SKILL.md

### Generate `{app-name}/references/workbench.md`

**Output path**:
```
{target-repo}/.manh-ai-harness/skills/{app-name}/references/workbench.md
```

**Source data** (already collected in W.2 and W.3):
- **W.2.C** — usage patterns per product (which products import `@ma-iris/ui-workbench`,
  which symbols, how used, any classes extending `WorkbenchWidget`)
- **W.2.D** — workbench metadata schemas (JSON files under `*/workbenches/*`)
- **W.3.C** — UI usage cross-reference for `component-ai-workbench` endpoints

Use this template (replace all bracketed placeholders with actual findings):

````markdown
# Workbench Usage: {app-name}

> Generated by: manh-workbench-analyzer
> Framework skill: [manh-ui-workbench-framework](../../manh-ui-workbench-framework/SKILL.md)

## Overview

[1–2 sentences: how many products use workbench, how many workbench pages are configured.
e.g. "3 of {N} products use the workbench framework; {M} workbench pages are defined in
ui-metadata across 2 products."]

## Workbench Pages (from ui-metadata)

| Page / Schema File | Type | Product | Widgets Defined | Datasource |
|--------------------|------|---------|----------------|-----------|
| `[path relative to repo root]` | `[WORKBENCH]` | `[product-key]` | [widget names] | [datasource config summary] |

If no workbench schema files are found, write:
> _No workbench metadata schemas detected in ui-metadata or product repos._

## Product × Workbench Usage

| Product | Workbench Pages | Widgets Extended | Services Used | Module Import |
|---------|----------------|-----------------|--------------|--------------|
| [{product-key}](../products/{product-key}.md) | [page names from W.2.D] | [CustomWidget extends WorkbenchWidget, or —] | [service names from W.2.C] | [Yes / No] |

## Widget Usage Summary

| Widget | Used By Products | Extended As | Purpose in This App |
|--------|----------------|-------------|---------------------|
| `[WidgetClass]` | [product keys] | `[CustomWidgetClass or —]` | [purpose inferred from usage context] |

## Backend API Cross-Reference

Workbench pages that call `component-ai-workbench` endpoints (from W.3.C):

| Product / Page | Endpoint Called | Via |
|----------------|----------------|-----|
| `[product-key / page name]` | `/api/ai-workbench/[path]` | [direct REST / command router] |

## Framework References

- Angular library → [ui-workbench.md](../../manh-ui-workbench-framework/references/ui-workbench.md)
- Backend facade → [component-ai-workbench.md](../../manh-ui-workbench-framework/references/component-ai-workbench.md)
````

---

### Generate framework skill

**Output path**:
```
{target-repo}/.manh-ai-harness/skills/manh-ui-workbench-framework/SKILL.md
```

Use this template:

````markdown
---
name: manh-ui-workbench-framework
version: [today's date YYYY-MM-DD]
description: |
  Reference skill for the workbench framework used by {app-name}.
  Covers the ui-workbench Angular library catalog and integration patterns,
  the component-ai-workbench backend facade APIs and command routing, and
  workbench JSON metadata schema patterns from ui-metadata.
trigger: manual
---

# manh-ui-workbench-framework

Workbench framework reference for `{app-name}`.

## Overview

The workbench framework provides configurable, data-driven workbench pages composed of
widgets. The frontend library (`ui-workbench`) provides the Angular host, widget
infrastructure, and schema definitions; the backend facade (`component-ai-workbench`)
exposes data APIs and command routing; JSON metadata files in `ui-metadata` configure
widget layout and data sources per workbench page instance.

## Reference Files

| File | What it covers |
|------|---------------|
| [ui-workbench.md](references/ui-workbench.md) | Angular library catalog (components, widgets, module, services, schemas), integration patterns, metadata schema inventory |
| [component-ai-workbench.md](references/component-ai-workbench.md) | Backend facade: native REST endpoints, command router entries, UI usage cross-reference, domain knowledge |

## When to Use These Skills

- **Building or modifying a workbench page** → start with `ui-workbench.md` (widget catalog + integration patterns)
- **Tracing a workbench API call to the backend** → see `component-ai-workbench.md` (REST endpoints + command router)
- **Understanding how a workbench page is configured via metadata** → see the Workbench Metadata Schemas section in `ui-workbench.md`
- **Registering a new widget or component** → see the Integration Patterns section in `ui-workbench.md`
````

### Update `{app-name}/SKILL.md`

Open `{target-repo}/.manh-ai-harness/skills/{app-name}/SKILL.md`.

**1. Populate `## Workbench Overview` table rows**

The `## Workbench Overview` section is already present in the template (written by Sub-step
7.3). Fill in each table row using W.2.C and W.2.D data:
- One row per product that has workbench usage (any file found in W.2.C)
- **Workbench Pages** column: names of JSON schema files from W.2.D that belong to this product
- **Widgets Used** column: widget class names used or extended in the product source (from W.2.C)

If no product uses workbench, replace the table row with:
```markdown
| — | No workbench usage detected | — |
```

**2. Add `workbench.md` to the Reference Files table**

The `workbench.md` reference row is already in the generated template. If the `{app-name}/SKILL.md`
was generated before this analyzer ran, append this row manually:
```markdown
| [workbench.md](references/workbench.md) | App-specific workbench usage: pages, widgets per product, metadata schema inventory, backend API cross-reference |
```

**3. Add `manh-ui-workbench-framework` to the Reference Files table**

If not already present, append:
```markdown
| [../manh-ui-workbench-framework/SKILL.md](../manh-ui-workbench-framework/SKILL.md) | Workbench framework: Angular library catalog, backend facade APIs, metadata schema definitions |
```

**4. Add When-to-Use bullets**

If not already present, append to the **When to Use These Skills** section:
```markdown
- **Understanding what workbench pages and widgets this app uses** → see `references/workbench.md`
- **Using or extending the workbench framework** → see `manh-ui-workbench-framework/SKILL.md`
```

After writing all files, run `bash .manh-ai-harness/setup.sh [--force]` to distribute
the `manh-ui-workbench-framework` skill, `{app-name}/references/workbench.md`, and the
updated `{app-name}` skill to all IDE tool directories.
