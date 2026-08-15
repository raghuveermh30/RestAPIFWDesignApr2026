---
name: manh-mup-repo-analyzer
version: 2026-06-04
description: |
  Specialist analyzer for Manhattan UI Platform (MUP) application repositories
  (e.g. ui-app-scp). Invoked automatically by manh-repo-analyzer when it detects
  @manh/mup-cli in package.json and mup.config.prod.json at the repo root.
  Runs 10 analysis layers:
    Layers 1-5: MUP config, NX workspace, JSON metadata schemas, MUP architecture
      patterns, build/test/conventions → generates repo-analysis.md
    Layer 6: generates manh-ui-mup-framework skill (fw-* API reference)
    Layer 7: generates app-level product skill set (per-product modules/routing/APIs)
    Layer 8: generates CommonUI Facade menu reference (component-commonui-facade)
    Layer 9: generates app-specific facade skills (component-aiui-facade etc.)
    Layer 10 (conditional): invokes manh-workbench-analyzer when ui-workbench is
      detected in mup.config.prod.json deps → generates manh-ui-workbench-framework skill
trigger: manual
---

# manh-mup-repo-analyzer

> **MANDATORY**: After completing any workflow, you MUST run `validator.py` as the final step — if it exists. If `validator.py` is not found, log `VALIDATOR SKIPPED` and proceed. See [Validation](#validation).

Specialist analyzer for MUP (Manhattan UI Platform) application repositories.
Produces `.manh-ai-harness/repo-analysis.md` in the standard format expected by
`manh-context-creator`, `manh-skill-generator`, and `manh-domain-classifier`.

## When to Use

This skill is normally invoked by `manh-repo-analyzer` after it detects MUP signals
(`@manh/mup-cli` dependency + `mup.config.prod.json`). You may invoke it directly if
you already know the repo is a MUP application and want to skip the detection step.

Do **not** invoke this skill for the MUP framework repos themselves (`ui-sc-core`,
`ui-sc-common`, `fw-ui-metadata`). Those are library repos analyzed by the standard
frontend/backend analyzers.

---

## MUP Framework Overview

MUP (Manhattan UI Platform) is a metadata-driven Angular application platform. A MUP
application repo consumes the MUP framework packages and provides:
- `mup.config.prod.json` — top-level app configuration (modules, routing, env targets)
- JSON metadata files — declarative UI schemas (pages, layouts, fields) read at runtime
- Angular app/libs built on `@manh/ui-sc-core` (renderer) and `@manh/ui-sc-common` (components)

---

## Analysis Workflow

### Step 0: Framework Repos Setup

Before any analysis begins, the skill requires read access to the 5 MUP framework
repositories. These are needed to understand the APIs, components, and contracts the
target application repo depends on.

#### 0.1 Check for User-Provided Paths

Ask the user:

```
This skill needs local copies of the 5 MUP framework repos to analyze the application.

Do you already have these repos checked out locally?
  [A] Yes — I'll provide the paths
  [B] No  — clone them automatically  ← pre-selected
```

**If [A] — user provides paths**: Ask for the local path to each repo:

```
Provide the local path for each repo (press Enter to skip and auto-clone that one):
  ui-metadata      path: ___
  ui-sc-core       path: ___
  ui-sc-common     path: ___
  fw-ui-metadata   path: ___
  fw-md-search     path: ___
```

Validate each provided path: confirm the directory exists and contains a `package.json`,
`build.gradle`, or `pom.xml` (to verify it is the right repo). If a path fails
validation, fall back to auto-clone for that repo.

**If [B] (or any repo path was skipped/invalid)**: proceed to Step 0.2.

#### 0.2 Clone Framework Repos

Clone destination folder:
```
{target-repo-parent}/mup-analyzer-dependent-repos/
```
where `{target-repo-parent}` is the parent directory of the target repo root (e.g., if
the target repo is at `/workspace/ui-app-scp`, the folder is
`/workspace/mup-analyzer-dependent-repos/`).

For each repo that was NOT provided by the user in Step 0.1, execute the following:

```bash
# If the repo directory already exists under mup-analyzer-dependent-repos, delete it first
if [ -d "{target-repo-parent}/mup-analyzer-dependent-repos/{repo-name}" ]; then
  rm -rf {target-repo-parent}/mup-analyzer-dependent-repos/{repo-name}
fi

# Clone fresh
git clone git@bitbucket.org:manhattanassociates/{repo-name}.git \
  {target-repo-parent}/mup-analyzer-dependent-repos/{repo-name}
```

**Repos to clone** (in order):

| Repo Name | Clone URL |
|-----------|-----------|
| `ui-metadata` | `git@bitbucket.org:manhattanassociates/ui-metadata.git` |
| `ui-sc-core` | `git@bitbucket.org:manhattanassociates/ui-sc-core.git` |
| `ui-sc-common` | `git@bitbucket.org:manhattanassociates/ui-sc-common.git` |
| `fw-ui-metadata` | `git@bitbucket.org:manhattanassociates/fw-ui-metadata.git` |
| `fw-md-search` | `git@bitbucket.org:manhattanassociates/fw-md-search.git` |

After all clones complete, confirm paths:

```
Framework repos ready:
  ✓ ui-metadata      → {target-repo-parent}/mup-analyzer-dependent-repos/ui-metadata
  ✓ ui-sc-core       → {target-repo-parent}/mup-analyzer-dependent-repos/ui-sc-core
  ✓ ui-sc-common     → {target-repo-parent}/mup-analyzer-dependent-repos/ui-sc-common
  ✓ fw-ui-metadata   → {target-repo-parent}/mup-analyzer-dependent-repos/fw-ui-metadata
  ✓ fw-md-search     → {target-repo-parent}/mup-analyzer-dependent-repos/fw-md-search

Proceeding with analysis of: {target-repo}
```

If any clone fails (e.g., SSH key not configured), display:
```
✗ Clone failed for {repo-name}: {error}
  Ensure SSH access to bitbucket.org is configured and retry.
  You can also provide a local path manually by re-running and choosing [A].
```
Halt and do not proceed until all 5 framework repos are available.

Store the resolved paths as `{fw-repo-paths}` — Layer 2 and Layer 4 use these to cross-reference
framework APIs against the target repo's imports and usage patterns.

#### Step 0.3: Clone Product Repos

Read `mup.config.prod.json` from the target repo root and navigate to the top-level `products`
object. Each key is a product name; its `origin` sub-object provides the clone coordinates:

```json
"products": {
  "{product-key}": {
    "origin": {
      "protocol": "ssh",
      "host": "bitbucket.org",
      "repoOwner": "manhattanassociates",
      "repo": "{repo-name}"
    }
  }
}
```

Construct the SSH clone URL as:
```
git@{origin.host}:{origin.repoOwner}/{origin.repo}.git
```

Clone each product into the same `{target-repo-parent}/mup-analyzer-dependent-repos/` folder
used for the framework repos. Apply the same **delete-before-clone** rule:

```bash
# For each product in mup.config.prod.json:
if [ -d "{parent}/mup-analyzer-dependent-repos/{origin.repo}" ]; then
  rm -rf {parent}/mup-analyzer-dependent-repos/{origin.repo}
fi
git clone git@{origin.host}:{origin.repoOwner}/{origin.repo}.git \
  {parent}/mup-analyzer-dependent-repos/{origin.repo}
```

After all clones complete, confirm:

```
Product repos ready:
  ✓ {product-key-1}  → {parent}/mup-analyzer-dependent-repos/{origin.repo-1}
  ✓ {product-key-2}  → {parent}/mup-analyzer-dependent-repos/{origin.repo-2}
  ...
```

If any clone fails, halt with the same SSH troubleshooting message as Step 0.2.

Store the resolved paths as `{product-repo-paths}` (map of product-key → local path) for use
in Layer 7.

#### Step 0.4: Clone `component-commonui-facade`

This repo contains the CommonUI Facade seed data including the menu structure definitions used
by MUP applications. Check for an existing local copy before cloning.

**Check order** (stop at the first match that contains
`commonui-facade/src/main/resources/seedData/`):

1. `{target-repo-parent}/component-commonui-facade/`
2. `{target-repo-parent}/mup-analyzer-dependent-repos/component-commonui-facade/`

If found, store that path as `{commonui-facade-path}` and skip cloning.

If neither exists, clone into `mup-analyzer-dependent-repos/`:

```bash
if [ -d "{target-repo-parent}/mup-analyzer-dependent-repos/component-commonui-facade" ]; then
  rm -rf {target-repo-parent}/mup-analyzer-dependent-repos/component-commonui-facade
fi

git clone git@bitbucket.org:manhattanassociates/component-commonui-facade.git \
  {target-repo-parent}/mup-analyzer-dependent-repos/component-commonui-facade
```

After the clone completes, confirm:

```
✓ component-commonui-facade → {commonui-facade-path}
```

If the clone fails, halt with:
```
✗ Clone failed for component-commonui-facade: {error}
  Ensure SSH access to bitbucket.org is configured and retry.
  You can also place the repo manually at:
    {target-repo-parent}/component-commonui-facade/
  and re-run.
```

Store the resolved path as `{commonui-facade-path}` for use in Layer 8.

#### Step 0.5: Detect Workbench Dependency

Read `mup.config.prod.json` from the target repo root and inspect the top-level `deps` object:

```json
"deps": {
  "ui-workbench": {
    "origin": { "host": "bitbucket.org", "repoOwner": "manhattanassociates", "repo": "ui-workbench" }
  }
}
```

- If `ui-workbench` is a key in `deps`:
  - Set `{workbench-detected} = true`
  - Store the `origin` sub-object as `{workbench-origin}` for use in Layer 10
- If `ui-workbench` is **not** present in `deps`:
  - Set `{workbench-detected} = false`
  - Layer 10 will be skipped entirely

---

### Layer 1: MUP Config Analysis

Read `mup.config.prod.json` from the repository root. Extract:

1. **App name** — the `name` or `appId` field
2. **Registered modules** — list all module entries (name, path, lazy/eager)
3. **Routing config** — top-level routes and their module bindings
4. **Environment targets** — any environment-specific overrides (`prod`, `uat`, `dev`)
5. **MUP package versions** — read `package.json` and record versions for:
   - `@manh/mup-cli`
   - `@manh/ui-sc-core` (if present)
   - `@manh/ui-sc-common` (if present)
   - Any other `@manh/*` packages

Build this summary table:

| Field | Value |
|-------|-------|
| App name | [from mup.config.prod.json] |
| Module count | [N] |
| MUP CLI version | [version from package.json] |
| Environment targets | [prod / uat / dev / ...] |

---

### Layer 2: NX Workspace Structure

Apply the same analysis as `manh-frontend-repo-analyzer` Layers 1–4:

1. **Monorepo tooling** — NX version from `nx.json`, package manager, Node.js version
2. **Apps inventory** — for each directory in `apps/`:
   - Read `project.json`: type, build executor, output path, serve port
   - Infer purpose from directory name and top-level imports
3. **Libs taxonomy** — category × domain matrix from `libs/` subdirectory structure
4. **Import namespace map** — read `tsconfig.base.json` `compilerOptions.paths` in full

Note any MUP-specific import aliases (e.g., `@manh/ui-sc-core`, `@manh/ui-sc-common`).

---

### Layer 3: JSON Metadata Schema Inventory

Locate JSON metadata files that define pages/components declaratively. Check these locations
(adjust if `mup.config.prod.json` specifies a different path):

- `src/metadata/`
- `src/assets/ui-config/`
- `src/assets/metadata/`
- Any path referenced by a `metadataPath` or `schemaPath` field in `mup.config.prod.json`

For each JSON schema file found:

1. Identify its **schema type** from the top-level `type` or `schemaType` field
   (e.g., `PAGE`, `LAYOUT`, `GRID`, `FORM`, `NAVIGATION`)
2. Record **key structural fields** (e.g., `components`, `columns`, `actions`, `routes`)
3. Identify the **Angular consumer** — which module or component reads this schema type

Build the schema inventory table:

| Schema File | Type | Key Fields | Angular Consumer |
|-------------|------|------------|-----------------|
| [filename] | [type] | [fields] | [component/service] |

If no metadata files are found in well-known locations, grep for JSON files imported by
Angular services with "metadata", "schema", or "config" in their name:
```bash
grep -rn "\.json" src/ --include="*.ts" | grep -i "metadata\|schema\|config"
```

---

### Layer 4: MUP Architecture Patterns

Identify the key MUP-specific patterns in use:

#### 4.1 Metadata Rendering Pipeline

Locate the Angular service or component that reads JSON schemas and renders components
dynamically. Common signals:
- Services named `*MetadataService`, `*RendererService`, `*SchemaService`
- Imports from `@manh/ui-sc-core` in the app's root module or feature modules
- `DynamicComponentLoader` or similar pattern

Describe: which service reads schemas → how it maps `type` to Angular components →
where the component registry lives.

#### 4.2 Registry Pattern

Check for registries that map schema types to Angular components:
- Classes/services named `*Registry`, `*ComponentRegistry`, `*WidgetRegistry`
- `register()` / `resolve()` / `lookup()` methods
- Dynamic `import()` for lazy component loading

List the main registries and what they register.

#### 4.3 Feature Flag Usage

Scan TypeScript source for feature flag patterns:
```bash
grep -rn "isOn\|featureFlags\.isOn\|features\.isOn\|mupFlags" src/ --include="*.ts"
```

Adjust the grep pattern based on what is found — check `package.json` for a feature-flag
client library (e.g., `@manh/feature-flags`, `@manh/mup-flags`) and use its API name.

For each match, extract the flag ID string (e.g., `"OM-114763#2026-03"`) and record it.

**Output**: flag client library name + usage pattern (needed by `manh-feature-flags` Phase 5).

#### 4.4 State Management

Identify the state library and any MUP-specific layer:
- Check `package.json` for `@ngrx/store`, `@ngrx/effects`, `@reduxjs/toolkit`, or `@manh/state-*`
- Look for a namespace + key access pattern layered on top of the state library

#### 4.5 Offline / Local Storage

Check for offline-first patterns:
- `libs/local/` directory with IndexedDB/Dexie wrappers
- Service worker: `ngsw-config.json` or `mockServiceWorker.js`
- Which business domains have offline counterparts

---

### Layer 5: Build, Test & Conventions

**Build & Dev Commands** — derive from `package.json` scripts and `nx.json`:

| Command | Description |
|---------|-------------|
| `nx serve [app]` | Dev server |
| `nx build [app]` | Production build |
| `npx nx run-many -t test` | All unit tests |
| `npx nx affected -t test` | Affected tests only |
| `npx nx run-many -t component-test -p [project]` | Cypress component tests |
| `nx reset` | Clear NX cache |

Also note any MUP-specific pre-build steps (e.g., metadata validation, schema generation
triggered by `mup-cli`).

**Testing Patterns**:
- Jest unit tests per lib
- Cypress component tests in `libs/bindings/`
- Cypress E2E in `apps/[name]-e2e/`
- Mock strategy (MSW / `cy.intercept` / jest mocks)

**Conventions**:
- TypeScript strict mode (check `tsconfig.base.json`)
- Import discipline: use `@alias/` paths from `tsconfig.base.json` — no cross-lib relative imports
- Barrel exports via `src/index.ts`
- ESLint rules from `.eslintrc.json`

---

### Layer 6: Generate `manh-ui-mup-framework` Skill Set

Using the 5 framework repos cloned in Step 0 plus findings from Layers 1–5, generate a
dedicated skill set in the target repo that gives AI agents deep, actionable knowledge of
the MUP framework as it is used in this application. Each reference file has two parts:
a **Reference section** (what exists in the framework) and a **Usage/Patterns section**
(how the target repo actually uses it, with real examples extracted from source).

**Output path** (create or overwrite):
```
{target-repo}/.manh-ai-harness/skills/manh-ui-mup-framework/
├── SKILL.md
└── references/
    ├── ui-metadata.md
    ├── ui-sc-core.md
    ├── ui-sc-common.md
    ├── fw-ui-metadata.md
    └── fw-md-search.md
```

If the folder already exists, overwrite its contents (same replace-vs-skip logic as other
framework skills in Phase 0's staleness table).

After writing all files, run `bash .manh-ai-harness/setup.sh [--force]` to distribute
the new skill into all IDE tool directories.

---

#### Sub-step 6.1: Analyze `ui-metadata` repo

**Source**: `{fw-repo-paths}/ui-metadata/`

Scan all `.json` files recursively. For each file:
1. Read the top-level `type` or `schemaType` field to determine schema category
2. Record all top-level fields and their value types (string, array, object, enum, etc.)
3. Note any `$ref` or cross-schema references
4. Identify any field that looks like a component reference (e.g., fields named `component`,
   `widget`, `type`, `renderer`)

Build:
- **Schema type catalog**: all distinct `type` values found, with count of files per type
- **Per-type field table**: required vs optional fields, allowed values for enum fields
- **Field glossary**: reusable field names that appear across multiple schema types

---

#### Sub-step 6.2: Analyze `ui-sc-core` repo

**Source**: `{fw-repo-paths}/ui-sc-core/`

1. **Public API** — Read `public-api.ts` or `src/index.ts`; list every export (components,
   services, directives, pipes, tokens, interfaces)
2. **Component catalog** — For each exported Angular component:
   - Selector, `@Input()` bindings, `@Output()` events, brief description
3. **Service catalog** — For each exported service:
   - Injectable scope, public method signatures, description
4. **Injection tokens** — List tokens, their type, and purpose
5. **Module configuration** — `forRoot()` / `forFeature()` options if present
6. **Schema type → Component registry** — Find the registry that maps schema `type` strings
   to Angular components. Common patterns:
   - A `switch`/`if` block in a renderer component or service
   - A `Map<string, Type<any>>` or `Record<string, ComponentType>` object
   - A method like `register(type, component)` or `resolve(type)`
   
   Build the mapping table:

   | Schema `type` value | Angular Component class | Selector |
   |---------------------|------------------------|----------|
   | [type string] | [ComponentClass] | [selector] |

---

#### Sub-step 6.3: Analyze `ui-sc-common` repo

**Source**: `{fw-repo-paths}/ui-sc-common/`

1. **Public API** — Read `public-api.ts` or `src/index.ts`
2. **Component catalog** — For each exported component:
   - Selector, `@Input()` bindings, `@Output()` events, description
3. **Pipes & directives** — Name, usage syntax, description
4. **Shared base classes / utilities** — Names and purpose
5. **Module exports** — Which components are declared in each NgModule

---

#### Sub-step 6.4: Analyze `fw-ui-metadata` repo

**Source**: `{fw-repo-paths}/fw-ui-metadata/`

1. **Controller scan** — Find all Spring `@RestController` or JAX-RS `@Path` classes:
   ```bash
   grep -rn "@RestController\|@RequestMapping\|@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping\|@Path\|@GET\|@POST\|@PUT\|@DELETE" src/ --include="*.java" -l
   ```
2. **Endpoint table** — For each controller method, extract:
   - HTTP method, full path (base path + method path), query params, path variables
   - Request body class (if any), response class
   - Brief description from Javadoc or method name
3. **DTO catalog** — For each request/response class found above:
   - Class name, key fields with types
4. **Service layer** — List key service classes and their public methods

---

#### Sub-step 6.5: Analyze `fw-md-search` repo

**Source**: `{fw-repo-paths}/fw-md-search/`

Follow the same process as Sub-step 6.4. Additionally:

5. **Search query structure** — Identify the search request DTO:
   - Filter fields (name, type, operators supported)
   - Sort fields and direction options
   - Pagination parameters (page, size, cursor)
6. **Response format** — Identify the search response DTO:
   - Result list field, total count field, pagination metadata

---

#### Sub-step 6.6: Cross-reference with target repo

**Source**: target repo source (`src/`, `libs/`, `apps/`)

1. **ui-sc-core usage** — Find all imports of `@manh/ui-sc-core` or the alias configured
   in `tsconfig.base.json`:
   ```bash
   grep -rn "@manh/ui-sc-core\|ui-sc-core" src/ libs/ apps/ --include="*.ts" -l
   ```
   For each file found, note: which exported symbol is imported and how it is used.

2. **ui-sc-common usage** — Same for `@manh/ui-sc-common`:
   ```bash
   grep -rn "@manh/ui-sc-common\|ui-sc-common" src/ libs/ apps/ --include="*.ts" -l
   ```
   For template usage, also check `.html` files for component selectors found in 6.3.

3. **fw-ui-metadata HTTP calls** — Find Angular `HttpClient` calls to metadata endpoints:
   ```bash
   grep -rn "HttpClient\|http\.get\|http\.post\|http\.put\|http\.delete" \
     libs/ apps/ src/ --include="*.ts" | grep -i "metadata\|schema\|layout\|page"
   ```
   Map each Angular HTTP call to the corresponding fw-ui-metadata endpoint from 6.4.

4. **fw-md-search HTTP calls** — Same, filtered for search:
   ```bash
   grep -rn "HttpClient\|http\.get\|http\.post" \
     libs/ apps/ src/ --include="*.ts" | grep -i "search\|query\|find"
   ```
   Map each Angular HTTP call to the corresponding fw-md-search endpoint from 6.5.

Compile **usage examples** (file path + line range + snippet) for each framework symbol
that is actually used in the target repo — these go into the Usage/Patterns sections.

---

#### Sub-step 6.7: Write the 5 reference files

Generate each file under `references/` using the template below. Replace bracketed
placeholders with actual findings from 6.1–6.6.

---

##### Template: `references/ui-metadata.md`

```markdown
# Reference: ui-metadata — JSON Schema Catalog

> Source repo: `ui-metadata` | Used by: `ui-sc-core`, `ui-sc-common`
> See also: [ui-sc-core.md](ui-sc-core.md) (renderer mapping), [fw-ui-metadata.md](fw-ui-metadata.md) (serving endpoints)

## Schema Type Catalog

| Type | File Count | Description |
|------|-----------|-------------|
| [type] | [N] | [what this schema type represents] |

## Per-Type Field Reference

### `[TYPE]`

| Field | Required | Type | Allowed Values / Notes |
|-------|----------|------|------------------------|
| type | ✓ | string | `"[TYPE]"` |
| [field] | ✓ / — | [type] | [notes] |

## Field Glossary

| Field Name | Appears In | Purpose |
|------------|-----------|---------|
| [field] | [types] | [purpose] |

---

## Usage: Authoring Metadata in [target-repo-name]

### How to create a new `[TYPE]` schema

1. Create a new `.json` file in `[metadata path from Layer 3]`
2. Set the required fields: `type`, `[field1]`, `[field2]`
3. [Step-by-step guidance based on existing examples]

### Existing examples

```json
// [filename extracted from target repo or ui-metadata]
{
  "type": "[TYPE]",
  [real field values from an actual file]
}
```

### Common mistakes

- [mistake] → [correction]
```

---

##### Template: `references/ui-sc-core.md`

```markdown
# Reference: ui-sc-core — Angular Renderer Engine

> Source repo: `ui-sc-core` | Consumed by: [target-repo-name]
> See also: [ui-metadata.md](ui-metadata.md) (schema types), [ui-sc-common.md](ui-sc-common.md) (shared components)

## Public API Summary

### Components

| Selector | Class | Key Inputs | Key Outputs | Purpose |
|----------|-------|-----------|------------|---------|
| [selector] | [ClassName] | [inputs] | [outputs] | [purpose] |

### Services

| Class | Scope | Key Methods | Purpose |
|-------|-------|------------|---------|
| [ServiceClass] | [root/module] | [methods] | [purpose] |

### Injection Tokens

| Token | Type | Purpose |
|-------|------|---------|
| [TOKEN_NAME] | [type] | [purpose] |

### Module Configuration

```typescript
// forRoot() options
[options interface or example]
```

## Schema Type → Component Registry

| JSON `type` value | Angular Component | Selector |
|-------------------|------------------|---------|
| [type] | [ComponentClass] | [selector] |

---

## Usage: ui-sc-core in [target-repo-name]

### Importing in a feature module

```typescript
// [real file path from target repo]
[real import/module snippet]
```

### Rendering a metadata schema

```typescript
// [real file path from target repo]
[real usage snippet showing how schema is loaded and rendered]
```

### Registering a custom component

```typescript
// How to add a custom component to the renderer registry
[pattern extracted from codebase or inferred from registry API]
```
```

---

##### Template: `references/ui-sc-common.md`

```markdown
# Reference: ui-sc-common — Shared Angular Component Library

> Source repo: `ui-sc-common` | Consumed by: [target-repo-name], `ui-sc-core`
> See also: [ui-sc-core.md](ui-sc-core.md)

## Component Catalog

| Selector | Class | Key Inputs | Key Outputs | Purpose |
|----------|-------|-----------|------------|---------|
| [selector] | [ClassName] | [@Input() names] | [@Output() names] | [purpose] |

## Pipes & Directives

| Name | Type | Syntax | Purpose |
|------|------|--------|---------|
| [name] | pipe/directive | [usage] | [purpose] |

---

## Usage: ui-sc-common in [target-repo-name]

### Components used in this repo

| Selector | Used in file(s) | How it's used |
|----------|----------------|---------------|
| [selector] | [file path] | [context] |

### Template examples

```html
<!-- [real file path from target repo] -->
[real template snippet using a common component]
```
```

---

##### Template: `references/fw-ui-metadata.md`

```markdown
# Reference: fw-ui-metadata — Metadata REST API

> Source repo: `fw-ui-metadata` (Java library) | Called by: [target-repo-name] Angular services
> See also: [ui-metadata.md](ui-metadata.md) (schemas served), [fw-md-search.md](fw-md-search.md)

## REST Endpoint Catalog

| Method | Path | Query Params | Request Body | Response | Description |
|--------|------|-------------|-------------|----------|-------------|
| [GET/POST/...] | [/api/v1/...] | [params] | [BodyClass or —] | [ResponseClass] | [description] |

## Key DTOs

### `[ResponseClass]`

| Field | Type | Description |
|-------|------|-------------|
| [field] | [type] | [description] |

---

## Usage: Calling fw-ui-metadata from [target-repo-name]

### Angular service → endpoint mapping

| Angular Service | Method | Calls | Description |
|----------------|--------|-------|-------------|
| [ServiceClass] | [method()] | `[HTTP METHOD] [path]` | [what it fetches] |

### HTTP call example

```typescript
// [real file path from target repo]
[real HttpClient call snippet mapped to the fw-ui-metadata endpoint]
```
```

---

##### Template: `references/fw-md-search.md`

```markdown
# Reference: fw-md-search — Search & Data Retrieval REST API

> Source repo: `fw-md-search` (Java library) | Called by: [target-repo-name] Angular services
> See also: [fw-ui-metadata.md](fw-ui-metadata.md)

## REST Endpoint Catalog

| Method | Path | Query Params | Request Body | Response | Description |
|--------|------|-------------|-------------|----------|-------------|
| [GET/POST/...] | [/api/v1/...] | [params] | [BodyClass or —] | [ResponseClass] | [description] |

## Search Query Structure

### Request DTO: `[SearchRequestClass]`

| Field | Type | Purpose |
|-------|------|---------|
| filters | `[FilterClass][]` | Criteria array |
| sort | `[SortClass][]` | Sort fields + direction |
| page | int | Page number (0-based) |
| size | int | Page size |
| [other fields] | [type] | [purpose] |

### Filter operators supported
[List of comparison operators: EQ, IN, LIKE, GT, LT, etc.]

### Response DTO: `[SearchResponseClass]`

| Field | Type | Purpose |
|-------|------|---------|
| results / content | `[ItemClass][]` | Result list |
| totalCount / totalElements | long | Total matches |
| [pagination fields] | [type] | [purpose] |

---

## Usage: Calling fw-md-search from [target-repo-name]

### Angular service → endpoint mapping

| Angular Service | Method | Calls | Description |
|----------------|--------|-------|-------------|
| [ServiceClass] | [method()] | `[HTTP METHOD] [path]` | [what it searches] |

### Building a search request

```typescript
// [real file path from target repo]
[real search request construction snippet]
```
```

---

#### Sub-step 6.8: Write `SKILL.md` (index)

Generate this as the **last file**, after all 5 references are complete.

```markdown
---
name: manh-ui-mup-framework
version: 2026-06-04
description: |
  Reference and usage-guidance skill for the MUP (Manhattan UI Platform) framework
  as used in [target-repo-name]. Covers JSON metadata schemas (ui-metadata),
  Angular renderer (ui-sc-core, ui-sc-common), and Java data APIs
  (fw-ui-metadata, fw-md-search). Use this skill when authoring metadata JSON files,
  extending the renderer, using shared components, or calling backend metadata/search APIs.
trigger: manual
---

# manh-ui-mup-framework

Reference and usage guidance for the MUP framework as used in **[target-repo-name]**.

## Architecture Overview

MUP is a metadata-driven Angular platform. The data flow is:

```
ui-metadata (JSON schemas)
     ↓  served by
fw-ui-metadata / fw-md-search (Java REST APIs)
     ↓  fetched by Angular services in [target-repo-name]
ui-sc-core (Angular renderer — reads schema, renders components)
     ↓  uses shared primitives from
ui-sc-common (Angular shared component library)
     ↓  outputs rendered UI to
[target-repo-name] feature modules / pages
```

## Cross-Reference: Schema → Renderer → API

| JSON Schema `type` | Rendered by (`ui-sc-core`) | Data served by | Angular service in [target-repo-name] |
|--------------------|---------------------------|---------------|--------------------------------------|
| [type from ui-metadata] | [Component from registry] | [fw-* endpoint] | [ServiceClass.method()] |

## Reference Files

| File | What it covers |
|------|---------------|
| [ui-metadata.md](references/ui-metadata.md) | JSON schema type catalog, field reference, authoring guide |
| [ui-sc-core.md](references/ui-sc-core.md) | Angular renderer components, services, schema→component registry |
| [ui-sc-common.md](references/ui-sc-common.md) | Shared Angular component library, pipes, usage in [target-repo-name] |
| [fw-ui-metadata.md](references/fw-ui-metadata.md) | Java metadata REST endpoints, DTOs, Angular call patterns |
| [fw-md-search.md](references/fw-md-search.md) | Java search REST endpoints, query structure, Angular call patterns |

## When to Use This Skill

- **Adding a new page/view** → start with `ui-metadata.md` to author the metadata JSON
- **Customizing rendering** → see `ui-sc-core.md` for registry extension
- **Using a shared UI component** → see `ui-sc-common.md`
- **Calling the metadata backend** → see `fw-ui-metadata.md`
- **Building a search feature** → see `fw-md-search.md`
```

---

### Layer 7: Generate App-Level Product Skill Set

Using the product repos cloned in Step 0.3 plus findings from Layers 1–5 (app structure,
metadata schemas, architecture patterns), generate a per-product skill set under the
target repo's harness skills directory.

**Output path** (create or overwrite):
```
{target-repo}/.manh-ai-harness/skills/{app-name}/
├── SKILL.md                             ← app summary + cross-reference index
└── references/
    └── products/
        ├── {product-key-1}.md           ← e.g., ui-scp.md
        ├── {product-key-2}.md           ← e.g., allocation.md
        └── ...
```

`{app-name}` = the app name from `mup.config.prod.json` (`name` / `appId` field), or
the target repo directory name as fallback.  
`{product-key}` = the JSON key under `products` in `mup.config.prod.json`.

If the folder already exists, overwrite its contents.

After writing all files, run `bash .manh-ai-harness/setup.sh [--force]` to distribute.

---

#### Sub-step 7.1: Per-Product Repo Analysis

Repeat the following analysis for **each** product in `{product-repo-paths}`:

**A. Repo Structure**

Read `nx.json` and all `project.json` files. Build the lib/app taxonomy:
- Apps: name, type, build executor
- Libs: name, layer category (feature, data-access, ui, util), domain scope
- Main Angular module(s) and routing entry points

**B. ui-sc-core Usage**

```bash
grep -rn "@manh/ui-sc-core" src/ libs/ apps/ --include="*.ts" -l
```

For each file found: identify which exported symbol is imported (component, service,
injection token) and how it is used — renderer invocation, module import, registry
registration, or service injection. Record as a usage table.

**C. ui-sc-common Usage**

```bash
grep -rn "@manh/ui-sc-common" src/ libs/ apps/ --include="*.ts" -l
```

Also scan `.html` template files for component selectors catalogued in Sub-step 6.3.
Build a table of which selectors appear in this product's templates and in which files.

**D. Metadata Patterns**

Identify which JSON schema types (from the catalog built in Sub-step 6.1) are used in
this product:
- HTTP calls that fetch metadata schemas (grep for endpoint paths found in fw-ui-metadata)
- Bundled JSON imports from `ui-metadata` or local assets
- `MetadataService` / renderer service calls and the schema type arguments passed

**E. Domain Concepts**

- Key Angular feature modules with their declared route paths
- Core service classes: name, purpose, principal public methods
- Domain model interfaces/classes: name, key fields
- Product-specific HTTP services: which fw-* endpoints are called

**F. State Management**

```bash
grep -rn "createReducer\|createFeature\|createEffect\|createSelector" \
  src/ libs/ apps/ --include="*.ts" -l
```

For each match: feature/store slice name, effects overview, key selectors exported.

**G. Feature Flags**

```bash
grep -rn "isOn\|featureFlags\|mupFlags" src/ libs/ apps/ --include="*.ts"
```

For each flag call: extract the flag ID string, file path, and the feature it gates.

**H. Implementation Pattern Classification**

Classify each product as **METADATA-ONLY**, **HYBRID**, or **CUSTOM CODE** using the
following 5-step process:

**Step 1 — Measure metadata usage via CommonUI Facade menu entries**

Use the menu entries already parsed in **Layer 8** (Sub-step 8.3) as ground truth for
which routes a product is expected to serve:

1. From the Layer 8 menu hierarchy, collect all `Url` values whose `GroupName` matches
   this product (use the GroupName-to-product mapping confirmed in Layer 8, Sub-step 8.2).

2. For each menu `Url` value, check whether any Angular routing definition in the target
   repo or this product repo declares that path:
   ```bash
   grep -rn "'<url-path>'\|\"<url-path>\"" {product-repo-path}/ \
     --include="*.ts" --include="*.json" -l
   ```
   Run once per distinct `Url` value.

3. Count:
   - `{menu-routes-total}` — distinct menu `Url` values for this product
   - `{menu-routes-with-angular-code}` — URLs matched in Angular routing source
   - `{menu-routes-without-angular-code}` — URLs with no Angular routing match
     (served purely by the MUP metadata renderer)

4. Compute the **metadata coverage ratio**:
   ```
   metadata-coverage = {menu-routes-without-angular-code} / {menu-routes-total}
   ```

   If `{menu-routes-total} == 0` (no menu entries map to this product), fall back to:
   ```bash
   grep -rn "MetadataService\|loadSchema\|fetchSchema\|metadataPath\|schemaType" \
     src/ libs/ apps/ --include="*.ts" | wc -l
   ```
   Treat any result `> 0` as `metadata-coverage = 1.0`; otherwise `0.0`.

**Step 2 — Measure direct framework component usage**

Use the import counts already collected from steps B and C:
- `{ui-sc-core-import-count}` = number of TypeScript files importing from `@manh/ui-sc-core`
- `{ui-sc-common-import-count}` = number of TypeScript files importing from `@manh/ui-sc-common`

**Step 3 — Count custom Angular components**

```bash
grep -rn "@Component\b" src/ libs/ apps/ --include="*.ts" -l | wc -l
```

Store result as `{custom-component-count}`.

**Step 4 — Classify**

Apply these rules in order:

```
IF metadata-coverage >= 0.8 AND (ui-sc-core-imports == 0 AND ui-sc-common-imports == 0)
  → Pattern = METADATA-ONLY

IF metadata-coverage >= 0.8 AND (ui-sc-core-imports > 0 OR ui-sc-common-imports > 0)
  → Pattern = HYBRID  (metadata-driven routes but also pulls framework components directly)

IF 0.2 <= metadata-coverage < 0.8
  → Pattern = HYBRID  (mix of metadata-rendered and custom-coded routes)

IF metadata-coverage < 0.2 AND custom-component-count > 0
  → Pattern = CUSTOM CODE

IF metadata-coverage < 0.2 AND custom-component-count == 0
  → Pattern = HYBRID (default; note uncertainty — few menu routes found, needs manual review)
```

**Step 5 — Collect evidence**

Record:
- `{menu-routes-total}`, `{menu-routes-without-angular-code}`, computed ratio
- Up to 3 representative files supporting the classification
- One-sentence reason: e.g. "8 of 9 menu routes have no Angular routing match and
  no ui-sc-core imports" / "Loads schemas but also imports `ManhDataGridComponent`
  directly in 4 files" / "No schema loading; implements 12 custom `@Component` classes"

---

#### Sub-step 7.2: Write Per-Product Skill Files

Generate `references/products/{product-key}.md` for each product using this template.
Replace all bracketed placeholders with actual findings from 7.1.

```markdown
# Product: {product-key} (`{origin.repo}`)

> Part of: [{app-name}](../../SKILL.md)
> Framework refs: [ui-sc-core](../../../manh-ui-mup-framework/references/ui-sc-core.md) · [ui-sc-common](../../../manh-ui-mup-framework/references/ui-sc-common.md) · [ui-metadata](../../../manh-ui-mup-framework/references/ui-metadata.md)

## Implementation Pattern

**Pattern**: [METADATA-ONLY | HYBRID | CUSTOM CODE]

| Dimension | Detail |
|-----------|--------|
| Menu routes registered (from commonui-facade) | [N total] |
| Menu routes with no Angular code (metadata-rendered) | [N — X%] |
| Menu routes with Angular code (custom/hybrid) | [N — X%] |
| ui-sc-core direct imports | [N files] |
| ui-sc-common direct imports | [N files] |
| Custom Angular components | [N components] |

**Evidence**:
- `[file path]` — [why this file is evidence]
- `[file path]` — [why this file is evidence]

**Reason**: [1–2 sentences — e.g., "8 of 9 menu routes have no Angular routing match and
no ui-sc-core imports, indicating the MUP metadata renderer handles all UI rendering." /
"Loads metadata schemas but also directly imports `ManhDataGridComponent` in 4 files for
features the framework doesn't support natively."]

## Repo Structure

| Layer | Path | Purpose |
|-------|------|---------|
| [feature/data-access/ui/util] | [path] | [purpose] |

## ui-sc-core Usage

| Symbol | File(s) | How used |
|--------|---------|---------|
| [ComponentClass / ServiceClass / TOKEN] | [file path] | [rendering / registry / config / injection] |

## ui-sc-common Components Used

| Selector | File(s) | Context |
|----------|---------|---------|
| [selector] | [template file] | [purpose in this product] |

## Metadata Patterns

| Schema Type | How Loaded | Service / Method |
|-------------|-----------|-----------------|
| [PAGE/GRID/FORM/...] | [HTTP / bundled JSON / lazy import] | [ServiceClass.method()] |

## Domain Concepts

### Key Modules & Routes

| Module | Route | Purpose |
|--------|-------|---------|
| [ModuleName] | [/route] | [purpose] |

### Key Services

| Service | Purpose | Key Methods |
|---------|---------|-------------|
| [ServiceClass] | [purpose] | [method signatures] |

### Domain Models

| Interface / Class | Key Fields | Purpose |
|------------------|-----------|---------|
| [ModelClass] | [fields] | [what it represents] |

## State Management

| Store Slice | Key Effects | Key Selectors |
|-------------|-------------|--------------|
| [feature name] | [effect names] | [selector names] |

## Feature Flags

| Flag ID | File | Purpose |
|---------|------|---------|
| [flag string] | [file:line] | [what it gates] |
```

---

#### Sub-step 7.3: Write App-Level `SKILL.md`

Generate this **last**, after all product files are written.

```markdown
---
name: {app-name}
version: 2026-06-04
description: |
  App-level skill for {app-name}. Covers all {N} products registered in
  mup.config.prod.json — structure, ui-sc-core/ui-sc-common usage, metadata
  patterns, domain concepts, state management, and feature flags per product.
trigger: manual
---

# {app-name} — Product Skills

## Architecture Overview

{app-name} is a MUP application hosting {N} products. Each product is a separate
git repo contributing feature modules to the shell application. The MUP framework
(ui-sc-core, ui-sc-common) provides the renderer and shared components; the Java
libraries (fw-ui-metadata, fw-md-search) serve the metadata and data.

For framework details see: [manh-ui-mup-framework](../manh-ui-mup-framework/SKILL.md)

## Product Inventory

| Product Key | Repo | Domain Summary | Metadata Types Used | Feature Flags |
|-------------|------|---------------|-------------------|---------------|
| [{product-key}](references/products/{product-key}.md) | [{origin.repo}] | [domain] | [schema types] | [N] |

## Implementation Patterns

| Product | Pattern | Menu Routes (total) | Metadata-Rendered | Angular-Coded | ui-sc-core Files | ui-sc-common Files | Custom Components |
|---------|---------|--------------------|--------------------|--------------|-----------------|-------------------|------------------|
| [{product-key}](references/products/{product-key}.md) | [METADATA-ONLY / HYBRID / CUSTOM CODE] | [N] | [N — X%] | [N — X%] | [N] | [N] | [N] |

## Cross-Reference: Product × Framework Usage

| Product | ui-sc-core Symbols | ui-sc-common Selectors | Metadata Schema Types |
|---------|-------------------|------------------------|----------------------|
| [{product-key}] | [symbols] | [selectors] | [types] |

## Feature Flag Summary

| Product | Flag ID | Purpose |
|---------|---------|---------||
| [{product-key}] | [flag string] | [what it gates] |

## Workbench Overview

_(Only present if `ui-workbench` was detected in `mup.config.prod.json` deps)_

{app-name} uses the workbench framework (`@ma-iris/ui-workbench`) to render
configurable, data-driven workbench pages composed of widgets.

| Product | Workbench Pages (from ui-metadata) | Widgets Used |
|---------|------------------------------------|-------------|
| [{product-key}] | [workbench page names] | [widget class names] |

For full details → [workbench.md](references/workbench.md)

## Reference Files

| File | What it covers |
|------|---------------|
| [component-commonui-facade.md](references/component-commonui-facade.md) | CommonUI Facade menu structure, navigation hierarchy, feature flags on menu items |
| [workbench.md](references/workbench.md) | App-specific workbench usage: pages, widgets per product, metadata schema inventory, backend API cross-reference _(only present if ui-workbench detected)_ |
| [../manh-ui-workbench-framework/SKILL.md](../manh-ui-workbench-framework/SKILL.md) | Workbench framework: Angular library catalog, backend facade APIs, metadata schema definitions _(only present if ui-workbench detected in deps)_ |

## When to Use These Skills

- **Understanding a product's domain** → open the product's reference file
- **Finding which ui-sc-core components a product uses** → see Cross-Reference table above
- **Tracing a metadata schema through a product** → start at the product file's Metadata Patterns section
- **Reviewing feature flag usage across products** → see Feature Flag Summary above
- **Understanding app navigation / menu structure** → see `component-commonui-facade.md`
- **Understanding how a product builds its UI (metadata vs custom code)** → see Implementation Patterns table
- **Understanding what workbench pages and widgets this app uses** → see `references/workbench.md`
- **Using or extending the workbench framework** → see `manh-ui-workbench-framework/SKILL.md`
```

---

### Layer 8: Generate CommonUI Facade Menu Structure Reference

Using `{commonui-facade-path}` resolved in Step 0.4, scan the seed data directory, identify the
menu files that apply to the target repo, and generate a reference document added to the Layer 7
`{app-name}` skill.

**Output path** (create or overwrite):
```
{target-repo}/.manh-ai-harness/skills/{app-name}/references/component-commonui-facade.md
```

---

#### Sub-step 8.1: Scan Menu Files and Collect GroupNames

**Source**: `{commonui-facade-path}/commonui-facade/src/main/resources/seedData/`

1. Recursively find all files matching `menu_*.json` and `menu__*.json` across the root and all
   subdirectories (`sc/`, `omni/`, `tmsrp/`, `tmssp/`, `platform/`, `ven/`, `modeler/`, etc.).
2. For each file, read all `GroupName` field values from `Data` array items.
3. Also read every `menuGroup.json` / `menuGroup__*.json` file found — collect the `EntityKey` /
   `EntityValue` pairs as declared menu groups.
4. Build a summary table:

| Directory | Menu Files Found | GroupNames Seen |
|-----------|-----------------|-----------------|
| `seedData/` (root) | [file list] | [groups] |
| `seedData/sc/` | [file list] | [groups] |
| `seedData/omni/` | [file list] | [groups] |
| … | … | … |

---

#### Sub-step 8.2: Infer Applicable Menu Files and Confirm with User

Cross-reference the GroupNames collected above against the target repo:

1. Grep the target repo source for any GroupName string found in Sub-step 8.1:
   ```bash
   grep -rn "{groupName}" src/ libs/ apps/ --include="*.ts" --include="*.json" -l
   ```
   Run one grep per distinct GroupName.

2. Score each seedData subdirectory by how many of its GroupNames appear in the target repo.
   The subdirectory with the highest match count is the primary candidate. Also include
   root-level menu files if their GroupNames match.

3. Present findings and ask the user to confirm:

```
Analyzing menu files in component-commonui-facade/seedData...

Inferred mapping for [{target-repo-name}]:
  Primary subdirectory : seedData/{subdir}/
  GroupNames matched   : {groupName1}, {groupName2}
  Root-level files     : {yes — menu_N__CommonUi.json / none}
  Confidence           : HIGH / MEDIUM / LOW ({reason})

  Files that will be included:
    - seedData/{subdir}/menu_*.json  ({N} files)
    [+ seedData/menu_N__CommonUi.json  (if root-level match)]

Are these the correct menu files for [{target-repo-name}]?
  [Y] Yes, proceed
  [N] No, let me choose manually
```

If the user chooses **[N]**, display the full subdirectory list with their GroupNames:

```
Available seedData subdirectories:
  1. seedData/sc/      — GroupNames: dmuifacade, Mobile, eitools, fwuifacade
  2. seedData/omni/    — GroupNames: omuifacade
  3. seedData/tmsrp/   — GroupNames: [list]
  4. seedData/platform/— GroupNames: [list]
  ...
  R. Root-level only   — GroupNames: cfw

Enter the number(s) of the subdirectory/subdirectories to use (comma-separated):
```

Store the confirmed file list as `{menu-files}` for use in Sub-step 8.3.

---

#### Sub-step 8.3: Parse Menu Hierarchy

For all confirmed `{menu-files}`:

1. Collect every object from all `Data` arrays across all files into a flat list of menu items.
2. Build a lookup map: `MenuId → item`.
3. Resolve parent-child relationships using `ParentId` → `MenuId`. Items with `ParentId == "null"`
   or no `ParentId` are root nodes.
4. Sort children within each parent by `DisplayOrder` (ascending; items without `DisplayOrder`
   go last).
5. For each item, record these fields (use `—` when absent):
   - `MenuId`, `Name`, `DisplayName`, `GroupName`, `ParentId`, `Url`, `UrlParams`,
     `FeatureFlag`, `MenuResourceId`
6. Collect all items that have a `FeatureFlag` field into a separate feature-flag list.

---

#### Sub-step 8.4: Generate `component-commonui-facade.md`

Write the file at:
```
{target-repo}/.manh-ai-harness/skills/{app-name}/references/component-commonui-facade.md
```

Use this template:

````markdown
# Reference: component-commonui-facade — Menu Structure

> Source repo: `component-commonui-facade`
> seedData path: `commonui-facade/src/main/resources/seedData/{subdir}/`
> GroupName(s) used: {groupName1}, {groupName2}
> Generated by: manh-mup-repo-analyzer (Layer 8)

## Menu Hierarchy

| MenuId | DisplayName | ParentId | URL | GroupName | FeatureFlag |
|--------|-------------|----------|-----|-----------|-------------|
| [rootId] | [display] | — | — | [group] | — |
| [childId] | [display] | [rootId] | [/path] | [group] | [flag or —] |
| … | … | … | … | … | … |

*(Sorted: root nodes first, then children in DisplayOrder order)*

## Menu Tree (Visual)

```
{root menu item (MenuId)}
├── {child 1 DisplayName} → {Url}
│   ├── {grandchild 1a DisplayName} → {Url}  [FF: FLAG-ID]
│   └── {grandchild 1b DisplayName} → {Url}
├── {child 2 DisplayName} → {Url}
│   └── …
└── …
```

## Feature Flags on Menu Items

| MenuId | DisplayName | URL | Flag ID |
|--------|-------------|-----|---------|
| [menuId] | [name] | [/path] | [FLAG-ID#YYYY-MM] |

*(Empty table if no menu items carry a FeatureFlag field)*

## Source Files Used

| File | GroupName(s) | Item Count |
|------|-------------|------------|
| `seedData/{subdir}/menu_N__CommonUi.json` | [group] | [N] |
| … | … | … |
````

---

#### Sub-step 8.5: Update `{app-name}` SKILL.md

Open the `{app-name}` SKILL.md written by Sub-step 7.3 and verify the **Reference Files** table
already contains the `component-commonui-facade.md` row (it is pre-populated by the template).
If for any reason the row is missing, append it:

```markdown
| [component-commonui-facade.md](references/component-commonui-facade.md) | CommonUI Facade menu structure, navigation hierarchy, feature flags on menu items |
```

Also verify the **When to Use These Skills** section contains:
```markdown
- **Understanding app navigation / menu structure** → see `component-commonui-facade.md`
```

Add if missing. No other changes to the SKILL.md are required.

---

### Layer 9: Generate App-Specific Facade Component Skills

Identify the application-specific facade microservices used by the target repo and its products,
confirm each with the user, clone them, and generate per-facade reference files added to the
Layer 7 `{app-name}` skill. Each facade file documents its native REST endpoints, command router
forwarding rules, and the UI's actual usage patterns.

This layer handles **app-specific** facades only. `component-commonui-facade` is excluded — it is
covered by Layer 8.

---

#### Sub-step 9.1: Detect Facade Components Used by Target Repo

Scan the target repo and every product repo in `{product-repo-paths}` for TypeScript HTTP calls
that reference a facade component:

```bash
grep -rn "facade" {repo-root}/ --include="*.ts" | grep -i "/api/"
```

Run this for the target repo root and for each product repo path in `{product-repo-paths}`.

From each matching line, extract the **facade URL prefix** — the `/api/{name}` segment of the
URL that contains "facade" (e.g., `/api/aiui-facade`, `/api/ai-uifacade`).

Deduplicate across all repos and exclude `commonui-facade` (handled by Layer 8).

For each remaining distinct facade prefix, derive the Bitbucket repo name:
- Strip the `/api/` prefix → `aiui-facade`
- Prepend `component-` → `component-aiui-facade`
- Clone URL: `git@bitbucket.org:manhattanassociates/component-aiui-facade.git`

Build a detection summary table:

| Facade URL Prefix | Derived Repo Name | Referenced In (sample files) |
|-------------------|------------------|------------------------------|
| `/api/aiui-facade` | `component-aiui-facade` | `ui-scp/services/history-projections.service.ts`, … |
| `/api/ai-uifacade` | `component-ai-uifacade` | … |

If no facade URLs (outside commonui-facade) are found, skip the rest of Layer 9 and note
"No app-specific facade components detected" in the output.

---

#### Sub-step 9.2: Confirm Each Facade with User

For each detected facade, present a confirmation prompt **one at a time**:

```
Facade component detected for [{target-repo-name}]:
  Facade URL prefix : /api/{facade-name}
  Derived repo      : component-{facade-name}
  Referenced in     : {N} TypeScript files across products: {product-key list}

  Sample calls:
    {file-path-1}: {url-snippet-1}
    {file-path-2}: {url-snippet-2}

Generate a facade skill for component-{facade-name}?
  [Y] Yes — clone repo and generate skill file
  [N] No  — skip this facade
```

Collect all confirmed facades as `{confirmed-facades}` (list of `{facade-name, repo-name}` pairs).

If the user confirms none, skip Sub-steps 9.3–9.5 entirely.

---

#### Sub-step 9.3: Clone Each Confirmed Facade Repo

For each facade in `{confirmed-facades}`, check for an existing local copy before cloning.

**Check order** (stop at the first match that contains `src/main/`):

1. `{target-repo-parent}/component-{facade-name}/`
2. `{target-repo-parent}/mup-analyzer-dependent-repos/component-{facade-name}/`

If found, store that path as `{facade-path}` and skip cloning.

If neither exists, clone into `mup-analyzer-dependent-repos/`:

```bash
if [ -d "{target-repo-parent}/mup-analyzer-dependent-repos/component-{facade-name}" ]; then
  rm -rf {target-repo-parent}/mup-analyzer-dependent-repos/component-{facade-name}
fi
git clone git@bitbucket.org:manhattanassociates/component-{facade-name}.git \
  {target-repo-parent}/mup-analyzer-dependent-repos/component-{facade-name}
```

Confirm after each clone:
```
✓ component-{facade-name} → {facade-path}
```

On failure, halt with:
```
✗ Clone failed for component-{facade-name}: {error}
  Ensure SSH access to bitbucket.org is configured and retry.
  You can also place the repo manually at:
    {target-repo-parent}/component-{facade-name}/
  and re-run.
```

Store resolved path as `{facade-path}` for use in Sub-step 9.4.

---

#### Sub-step 9.4: Analyze Each Facade Repo

Repeat the following analysis for **each** confirmed facade:

##### A. Native REST Endpoints

Find all Spring `@RestController` classes:
```bash
grep -rn "@RestController" {facade-path}/src/main/java/ --include="*.java" -l
```

For each controller file:
1. Read the class-level `@RequestMapping` value to get the **base path**
2. For each method annotated with `@GetMapping`, `@PostMapping`, `@PutMapping`,
   `@DeleteMapping`, or `@PatchMapping`:
   - **HTTP method**: infer from annotation name
   - **Full path**: combine base path + method-level mapping value
   - **Request body**: parameter annotated with `@RequestBody` (class name only)
   - **Return type**: method return type (unwrap `ResponseEntity<T>` → `T`)
   - **Description**: first sentence of Javadoc `/** */` if present; otherwise derive
     a readable description from the method name (e.g., `searchItemLocationDetails` →
     "Search item location details")
3. **Group by domain**: use the Java package sub-path below the facade root package
   (e.g., `…aiui.forecast.rest` → domain `forecast`; `…aiui.allocation.rest` → `allocation`)

Build one endpoint table per domain:

```
### {Domain} Endpoints

| HTTP Method | Full Path | Request Body | Response Type | Description |
|-------------|-----------|-------------|--------------|-------------|
| POST | `/api/{facade}/itemLocationDetails/search` | `QueryDTO` | `RestApiResponse` | Search item location details |
```

##### B. Command Router Entries

Read `{facade-path}/src/main/resources/component.properties`.

Find the facade prefix from:
```
manh.command-router.prefix=/api/{facade-name}
```

Parse every group of three properties that share the same command key:
```
manh.command-router.commands.{commandKey}.commandPath=...
manh.command-router.commands.{commandKey}.targetComponent=...
manh.command-router.commands.{commandKey}.targetEndpoint=...
```

Build the command router table:

```
| Command Key | Facade Path (UI calls this) | Routes To Component | Target Endpoint |
|-------------|----------------------------|--------------------|--------------------|
| suggestedOrderLineSave | `/suggestedOrderLine/save` | `com-manh-cp-ai-inventoryoptimization` | `/api/ai-inventoryoptimization/suggestedOrderLine/save` |
```

Group by `targetComponent` to make the table easier to read.

##### C. UI Usage Cross-Reference

From the grep results collected in Sub-step 9.1, build a usage table for this specific facade.
Only include endpoints that match this facade's URL prefix:

```
| UI File (path relative to product repo) | HTTP Method | Endpoint Called | Domain |
|----------------------------------------|------------|----------------|--------|
| `services/history-projections.service.ts` | POST | `/api/aiui-facade/itemLocationDetails/search` | item-location |
```

Infer HTTP method from the Angular `HttpClient` call (`http.get` → GET, `http.post` → POST, etc.).

---

#### Sub-step 9.5: Generate `component-{facade-name}.md` and Update SKILL.md

**Output path** for each confirmed facade:
```
{target-repo}/.manh-ai-harness/skills/{app-name}/references/component-{facade-name}.md
```

Use this template (replace bracketed placeholders with actual findings from Sub-step 9.4):

````markdown
# Reference: component-{facade-name} — Facade REST API & Command Router

> Source repo: `component-{facade-name}`
> Facade API prefix: `/api/{facade-name}`
> Generated by: manh-mup-repo-analyzer (Layer 9)

## Overview

`component-{facade-name}` is a backend facade microservice. It:
- Exposes **native REST APIs** under `/api/{facade-name}/` — business logic implemented in this component
- **Routes UI calls** to other backend components via the Command Router — acts as a consistent
  security and grant boundary for menu-linked APIs

## Native REST Endpoints

### {Domain 1}

| HTTP Method | Path | Request Body | Response | Description |
|-------------|------|-------------|----------|-------------|
| [GET/POST/…] | `/api/{facade-name}/[path]` | [BodyClass or —] | [ResponseType] | [description] |

### {Domain 2}

| HTTP Method | Path | Request Body | Response | Description |
|-------------|------|-------------|----------|-------------|
| … | … | … | … | … |

## Command Router Entries

Calls from the UI to these facade paths are transparently forwarded to another backend component.
The facade enforces grants and resource rules before forwarding.

Facade prefix: `/api/{facade-name}`

### Routed to: `{targetComponent-1}`

| Command Key | UI Calls (facade path) | Forwarded To |
|-------------|----------------------|--------------|
| [commandKey] | `/[commandPath]` | `[targetEndpoint]` |

### Routed to: `{targetComponent-2}`

| Command Key | UI Calls (facade path) | Forwarded To |
|-------------|----------------------|--------------|
| … | … | … |

## UI Usage in [{target-repo-name}]

Endpoints actually called by this application's product repos:

| UI File | HTTP Method | Endpoint | Domain |
|---------|------------|---------|--------|
| [file path] | [GET/POST/…] | `/api/{facade-name}/[path]` | [domain] |

## Domain Knowledge

[For each domain grouping found in the endpoint catalog, write 2–4 sentences explaining the
business purpose — e.g.:
"**Forecast**: Endpoints in this domain retrieve historical demand data and forward-looking
projection details for item-location combinations. They are used by the Forecast Details page
to render history/projection charts."]
````

After writing each file, open the `{app-name}` SKILL.md and append a row to the **Reference Files** table:

```markdown
| [component-{facade-name}.md](references/component-{facade-name}.md) | {facade-name} native REST endpoints, command router forwarding rules, UI usage mapping |
```

Also append to the **When to Use These Skills** section:

```markdown
- **Calling {facade-name} APIs or tracing command router routing** → see `component-{facade-name}.md`
```

Repeat Sub-steps 9.4–9.5 for every facade in `{confirmed-facades}`.

After all facades are processed, run `bash .manh-ai-harness/setup.sh [--force]` to distribute
the updated `{app-name}` skill (including the new reference files) to all IDE tool directories.

---

### Layer 10: Workbench Framework Analysis

> **Conditional** — only execute if `{workbench-detected} == true` (set in Step 0.5).
> If `{workbench-detected} == false`, skip this layer entirely.

Invoke `manh-workbench-analyzer` as documented in
`references/manh-workbench-analyzer.md`. Pass the following resolved values:

| Variable | Value |
|----------|-------|
| `{ui-workbench-path}` | Resolved in Sub-step W.1 (clone or local) |
| `{ai-workbench-path}` | Resolved in Sub-step W.1 (clone or local) |
| `{workbench-origin}` | Stored in Step 0.5 from `mup.config.prod.json` |
| `{fw-ui-metadata-path}` | Already resolved in Step 0.1/0.2 (framework repos) |
| `{product-repo-paths}` | Already resolved in Step 0.3 |
| `{target-repo}` | Target repository root |
| `{app-name}` | From Layer 1 |

The analyzer will generate:
```
{target-repo}/.manh-ai-harness/skills/manh-ui-workbench-framework/
├── SKILL.md
└── references/
    ├── ui-workbench.md
    └── component-ai-workbench.md
```
and update `{target-repo}/.manh-ai-harness/skills/{app-name}/SKILL.md` with a reference
row and When-to-Use bullet.

---

## Output: `.manh-ai-harness/repo-analysis.md`

Generate using this template (replace example content with actual findings):

```markdown
# Repository Analysis: [repo-name]

Generated: [timestamp]
Generated by: manh-mup-repo-analyzer
Repo Type: MUP Application (NX [version] / Angular [version])

## 0. MUP Framework

- **App name**: [from mup.config.prod.json]
- **MUP CLI version**: [version]
- **Registered modules**: [count] — [list module names]
- **Environment targets**: [prod, uat, dev, ...]
- **MUP packages consumed**: [list @manh/* packages with versions]
- **JSON Metadata Schemas**: [count] schema files found — types: [PAGE, LAYOUT, GRID, ...]
- **Feature flag client**: [library name and usage pattern, or "not detected"]

## 1. Workspace Overview

- **Monorepo Tooling**: NX [version]
- **UI Framework**: Angular [version]
- **Package Manager**: npm / yarn / pnpm
- **Node.js**: [version]
- **NX Scope**: @[scope]

## 2. Apps Inventory

| App | Type | Build Executor | Output | Port | Purpose |
|-----|------|----------------|--------|------|---------|
| [name] | application | [executor] | [path] | [port] | [purpose] |

## 3. Libs Matrix (Category × Domain)

|            | common | [domain1] | [domain2] | [other] |
|------------|--------|-----------|-----------|---------|
| ui         |   ✓    |     ✓     |     ✓     |    -    |
| bindings   |   ✓    |     ✓     |     -     |    -    |
| services   |   ✓    |     ✓     |     ✓     |    -    |
| models     |   ✓    |     ✓     |     ✓     |    -    |
| local      |   ✓    |     ✓     |     -     |    -    |

## 4. Import Namespace Map

| Alias | Physical Path | Category | Domain |
|-------|---------------|----------|--------|
| @manh/ui-sc-core | [path] | framework | mup |
| @manh/ui-sc-common | [path] | framework | mup |
| [all aliases from tsconfig.base.json] | ... | ... | ... |

## 5. JSON Metadata Schema Inventory

| Schema File | Type | Key Fields | Angular Consumer |
|-------------|------|------------|-----------------|
| [filename.json] | [PAGE/LAYOUT/GRID/...] | [fields] | [component] |

## 6. MUP Architecture Patterns

### Metadata Rendering Pipeline
[service/component that reads schemas → component registry → dynamic rendering]

### Registry Pattern
[registries found and what they register, or "Not detected"]

### Feature Flag Usage
- **Client library**: [e.g., @manh/mup-flags]
- **Usage pattern**: `[featureFlagService.isOn("FLAG-ID")]`
- **Flags found in source**: [list flag IDs or "none detected"]

### State Management
- **Library**: [NgRx / Redux Toolkit / other]
- **Custom layer**: [describe if present]

### Offline Capability
[present / not present — details if present]

## 7. Build & Dev Commands

| Command | Description |
|---------|-------------|
| `nx serve [app]` | Dev server |
| `nx build [app]` | Production build |
| `npx nx run-many -t test` | All unit tests |
| `npx nx run-many -t component-test -p [project]` | Cypress component tests |

## 8. Testing Patterns

### Jest (Unit)
- Config: `jest.config.ts` root + per-project
- Command: `npx nx run-many -t test`

### Cypress Component Tests
- Location: `libs/bindings/[project]/src/lib/tests/`
- Command: `npx nx run-many -t component-test -p [project]`

### Cypress E2E
- Location: `apps/[name]-e2e/`
- Command: `npx nx run [name]-e2e:e2e`

## 9. Code Conventions

- **TypeScript**: [strict mode on/off]
- **Import discipline**: use `@alias/` paths — no relative cross-lib imports
- **Barrel exports**: all public APIs via `src/index.ts`
- **ESLint**: [key rules]

## 10. Complexity Hotspots

[High-complexity areas that warrant dedicated skills]
1. [Area]: [why it's complex]

## 11. Skill Recommendations

- `manh-mup-metadata-schema` — JSON schema authoring, field types, schema-to-component mapping
- `manh-mup-registry` — component/service registry usage and extension patterns
- `manh-mup-feature-flags` — MUP-specific feature flag patterns (TypeScript client)
- `manh-[domain]-domain` — domain-specific component and service patterns
- `manh-ui-testing` — Jest, Cypress component tests, NX test targets
```

---

## Tips

- **Read `mup.config.prod.json` first** — it is the authoritative entry point for the app's module and routing structure
- **Check `tsconfig.base.json` paths early** — agents writing code must use these import aliases
- **Metadata schema inventory is critical** — document types and consumers clearly; this drives skill generation
- **Feature flag client name varies** — always grep source to find the actual API before recording the pattern
- **Document uncertainty** — mark inferred patterns as "observed" vs "confirmed"
- **Think like an agent** — the most useful output tells an agent where to look first and what not to touch

---

### Step 1 — Run Validator (MANDATORY)

Use the Bash tool to execute this command now:

```bash
SKILL_VALIDATOR=".manh-ai-harness/skills/manh-mup-repo-analyzer/validator.py"
if [ -f "$SKILL_VALIDATOR" ]; then
  DIFF_CONTENT=$(git diff HEAD 2>/dev/null || git diff 2>/dev/null)
  CHANGED_FILES=$(git diff --name-only HEAD 2>/dev/null | tr '\n' ',' | sed 's/,$//')
  python3 "$SKILL_VALIDATOR" \
    --diff <(echo "$DIFF_CONTENT") \
    --files "${CHANGED_FILES:-/dev/null}" \
    --trace /dev/null
else
  echo "VALIDATOR SKIPPED: $SKILL_VALIDATOR not found"
fi
```


### Final Step — Run Validator

You MUST run the validator as the last step of every workflow. Execute this block exactly:

```bash
SKILL_VALIDATOR=".manh-ai-harness/skills/manh-mup-repo-analyzer/validator.py"
if [ -f "$SKILL_VALIDATOR" ]; then
  DIFF_CONTENT=$(git diff HEAD 2>/dev/null || git diff 2>/dev/null)
  CHANGED_FILES=$(git diff --name-only HEAD 2>/dev/null | tr '\n' ',' | sed 's/,$//')
  python3 "$SKILL_VALIDATOR" \
    --diff <(echo "$DIFF_CONTENT") \
    --files "${CHANGED_FILES:-/dev/null}" \
    --trace /dev/null
else
  echo "VALIDATOR SKIPPED: $SKILL_VALIDATOR not found"
fi
```

If the validator is not found, log `VALIDATOR SKIPPED` and proceed. Follow the Re-Analysis Protocol in the Validation section for exit code 1. For exit code 2, log `VALIDATOR SKIPPED` and proceed.

---

## Validation

**MANDATORY post-execution check.** You MUST run `validator.py` after completing any workflow in this skill. Do NOT skip this step. If it fails (exit 1), follow the Re-Analysis Protocol below — do not treat the failure as blocking without analysis.

### How to Run

Use the Bash tool to execute this command now:

```bash
SKILL_VALIDATOR=".manh-ai-harness/skills/manh-mup-repo-analyzer/validator.py"
if [ -f "$SKILL_VALIDATOR" ]; then
  DIFF_CONTENT=$(git diff HEAD 2>/dev/null || git diff 2>/dev/null)
  CHANGED_FILES=$(git diff --name-only HEAD 2>/dev/null | tr '\n' ',' | sed 's/,$//')
  python3 "$SKILL_VALIDATOR" \
    --diff <(echo "$DIFF_CONTENT") \
    --files "${CHANGED_FILES:-/dev/null}" \
    --trace /dev/null
else
  echo "VALIDATOR SKIPPED: $SKILL_VALIDATOR not found"
fi
```

### Validation Contract (`skill.yaml`)

| Field | Value |
|---|---|
| `skill_id` | `manh-mup-repo-analyzer` |
| `validation_mode` | `soft` |
| `severity` | `critical` |
| `entrypoint` | `validator.py` |

### What the Validator Checks

| Check | Severity | Description |
|---|---|---|
| mup.config.prod.json read | Critical | Must be read before MUP analysis begins |
| NX workspace analyzed | Critical | nx.json, apps/, libs/, tsconfig.base.json must be scanned |
| JSON metadata schemas inventoried | Critical | Schema types (PAGE, LAYOUT, GRID, FORM) must be catalogued |
| MUP architecture patterns detected | Critical | Renderer, registry, ui-sc-core usage must be identified |
| repo-analysis.md produced | Critical | Must be written to .manh-ai-harness/ |
| Framework skill generated | Critical | manh-ui-mup-framework/SKILL.md must be created (Layer 6) |
| Core layer coverage | Critical | At least 3 of 5 core layers (L1-L5) must have evidence |
| setup.sh run | Warning | Should be run to distribute generated skills |

**Exit code `0`** = passed — no further action. **Exit code `1`** = soft fail — re-analyze violations before proceeding.
### Pre-flight Check

Before running the validator, verify the file exists:

```bash
if [ ! -f .manh-ai-harness/skills/manh-mup-repo-analyzer/validator.py ]; then
  echo "⚠ validator.py not found — skipping validation (validator was not distributed to this repo)"
fi
```

If `validator.py` is not found, log `VALIDATOR SKIPPED: validator.py not present at expected path` and proceed. Do NOT error, loop, or attempt to locate it elsewhere.

### Re-Analysis Protocol

**On exit 0**: Passed — no action needed.

**On exit 1** (soft fail — validator ran but found violations):
1. Parse the JSON output and read each violation message
2. Compare each violation against the artifacts you actually produced
3. **False positive** (content is correct but regex didn't match): log
   `VALIDATOR OVERRIDE: [violation] — Reason: [justification]` and proceed
4. **Real gap**: attempt to fix (one attempt only), then re-run `validator.py`
5. If re-run passes → done. If it still fails → log remaining violations as
   warnings and proceed

**On exit 2** (file not found or Python error):
Log `VALIDATOR SKIPPED: validator.py not found or failed to execute` and proceed.
Do NOT retry. Do NOT attempt to locate the file elsewhere. This means the validator
was not distributed to this repo — it is not a workflow failure.

Never retry more than once. Never block indefinitely on a validator failure.
Include a Validator Results table in your output summarizing each check as
PASS, OVERRIDE (with reason), FIXED, SKIPPED, or WARNING.
