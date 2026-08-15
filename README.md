# REST API Test Automation Framework

A reusable, production-grade REST API test automation framework built with **Java 11**, **Rest Assured**, and **TestNG**. Designed as a reference implementation that can be adapted to any project requiring automated API test coverage across multiple environments.

---

## Tech Stack

| Tool / Library         | Version  | Purpose                                      |
|------------------------|----------|----------------------------------------------|
| Java                   | 11       | Core language                                |
| Maven                  | 3.x      | Build and dependency management              |
| Rest Assured           | 5.5.0    | HTTP client for API calls and assertions     |
| TestNG                 | 7.10.2   | Test runner and suite management             |
| Jackson Databind       | 2.18.2   | JSON/XML serialization and deserialization   |
| Lombok                 | 1.18.36  | Boilerplate reduction (POJOs)                |
| JsonPath               | 2.9.0    | JSON response extraction                     |
| Allure TestNG          | 2.29.1   | Rich HTML test reporting                     |
| ChainTest TestNG       | 1.0.12   | AI-assisted test reporting                   |
| ExtentReports          | 5.1.1    | Alternate HTML reporting                     |
| WireMock               | 3.9.2    | API mocking and stubbing                     |
| Apache POI             | 3.9      | Excel-based data-driven testing              |
| ScribeJava             | 2.5.3    | OAuth 2.0 token generation                   |

---

## Project Structure

```
src/
├── main/java/com/qa/api/
│   ├── client/          # RestClient — generic HTTP wrapper (GET, POST, PUT, PATCH, DELETE)
│   ├── constants/       # AuthType enum, StatusCode, AppConstants
│   ├── errors/          # ApiErrors
│   ├── exception/       # Custom APIException
│   ├── manager/         # ConfigManager — reads environment .properties files
│   ├── pojo/            # Lombok POJOs: User, Products, Credentials
│   ├── schema/          # SchemaValidator — JSON schema contract validation
│   └── utils/           # ExcelUtils, CSVReaderUtil, JsonUtil, JsonPathValidatorUtil, XmlPathUtil, ObjectMapperUtils, StringUtils
│
└── test/java/com/qa/api/
    ├── base/            # BaseTest — common setup and teardown
    ├── gorest/tests/    # CRUD tests: GetUser, CreateUser, UpdateUser, DeleteUser, GetSingleUserWithSerialization
    ├── reqres/tests/    # ReqRes API tests
    ├── products/        # Products API tests (JsonPath and POJO-based)
    ├── contacts/test/   # Contacts API tests
    ├── amedus/test/     # Amadeus travel API test (OAuth 2.0 flow)
    ├── basicauth/       # Basic Auth test
    └── scematest/       # JSON schema validation test

test/resources/
├── config_dev.properties    # Dev environment config
├── config_qa.properties     # QA environment config
├── config_uat.properties    # UAT environment config
├── config_stage.properties  # Stage environment config
├── config_prod.properties   # Prod environment config
├── jsons/                   # Request body JSON files
├── schema/                  # JSON schema files for contract testing
└── testrunners/             # TestNG XML suite files
```

---

## Key Design Features

### 1. Generic REST Client
`RestClient.java` provides a single, reusable entry point for all HTTP methods. Test classes call it directly without repeating Rest Assured boilerplate. Allure `@Step` annotations are applied so every call is traced in reports.

### 2. Multi-Environment Configuration
`ConfigManager` resolves the target environment from a system property (`env`) at runtime and loads the corresponding `config_<env>.properties` file. No base URLs or credentials are hardcoded in tests.

```bash
# Run against QA environment
mvn test -Denv=qa

# Run against UAT environment
mvn test -Denv=uat
```

### 3. Authentication Support
The `AuthType` enum drives auth selection inside `RestClient`:

| AuthType       | Mechanism                             |
|----------------|---------------------------------------|
| `NO_AUTH`      | No authorization header               |
| `BEARER_TOKEN` | `Authorization: Bearer <token>`       |
| `BASIC_AUTH`   | `Authorization: Basic <base64>`       |
| `API_KEY`      | `x-api-key` header                    |
| OAuth 2.0      | Client credentials flow via ScribeJava / form params |

### 4. POJO-Based Serialization
Jackson + Lombok POJOs (`User`, `Products`, `Credentials`) are used for both request body construction and response deserialization. `JsonUtil.deserialize(response, MyClass.class)` handles response → POJO conversion.

### 5. JSON Schema Validation
`SchemaValidator` and Rest Assured's `json-schema-validator` module validate API responses against JSON Schema files stored in `src/test/resources/schema/`. This catches contract regressions early.

### 6. Data-Driven Testing
- `ExcelUtils` — reads test data from `.xlsx` files via Apache POI
- `CSVReaderUtil` — reads test data from `.csv` files
- TestNG `@DataProvider` wires data into test methods

### 7. API Mocking
WireMock is included for stubbing external dependencies, enabling tests to run without real downstream services.

### 8. Reporting
Three reporting options are wired in simultaneously:
- **Allure** — detailed HTML report with request/response logs per step
- **ChainTest** — AI-assisted test insights (configured via `chaintest.properties`)
- **ExtentReports** — lightweight standalone HTML report

---

## APIs Covered

| API             | Base URL                        | Tests                                     |
|-----------------|---------------------------------|-------------------------------------------|
| GoRest          | `https://gorest.co.in`          | GET, POST, PUT, PATCH, DELETE users       |
| ReqRes          | `https://reqres.in`             | GET users, schema validation              |
| Products        | (configurable)                  | GET products with JsonPath and POJO       |
| Contacts        | (configurable)                  | GET and POST contacts                     |
| Amadeus         | (OAuth 2.0 travel API)          | Token fetch + authenticated API call      |
| Basic Auth API  | (configurable)                  | Authorization: Basic header validation    |

---

## Running Tests

**Prerequisites:** Java 11+, Maven 3.x

```bash
# Run default sanity suite (configured in pom.xml)
mvn test

# Run a specific TestNG XML suite
mvn test -DsuiteXmlFile=src/test/resources/testrunners/gorest_regression.xml

# Run against a specific environment
mvn test -Denv=qa

# Generate Allure report
mvn allure:report
```

**Available TestNG suites:**

| Suite file               | Scope                              |
|--------------------------|------------------------------------|
| `testng_sanity.xml`      | Sanity — UpdateUser, Product, Schema, BasicAuth |
| `testng_regression.xml`  | Full regression                    |
| `gorest_regression.xml`  | GoRest CRUD regression             |
| `products_regression.xml`| Products API regression            |
| `schema_regression.xml`  | Schema contract regression         |

---

## Configuration

Each `config_<env>.properties` file follows this structure:

```properties
baseurl=https://gorest.co.in/
baseurl.gorest=https://gorest.co.in
baseurl.reqres=https://reqres.in
bearerToken=<your_token>
basicUserName=admin
basicPassword=admin
clientId=<oauth_client_id>
clientSecret=<oauth_client_secret>
grant_type=client_credentials
apikey=<your_api_key>
```

> **Note:** Never commit real tokens or secrets. Use environment variables or a secrets manager for CI/CD pipelines.

---

## Test Case Coverage

| # | Concept | Test Class | Scenarios |
|---|---------|-----------|-----------|
| 1 | GET — all records | `GetUserTest` | getAllUsers, getAllUsersWithQueryParam, getSingleUser |
| 2 | POST — create | `CreateUserTest` | inline DataProvider, Excel data-driven, raw string body, file body |
| 3 | PUT — full update | `UpdateUserTest` | POST→GET→PUT→GET (AAA pattern) |
| 4 | PATCH — partial update | `PatchUserTest` | patch status only, patch name only; verifies other fields unchanged |
| 5 | DELETE | `DeleteUserTest` | POST→GET→DELETE→GET (verifies 404 after delete) |
| 6 | Negative testing | `NegativeGoRestTest` | 401 no token, 401 invalid token, 404 nonexistent, 422 missing fields, 422 duplicate email |
| 7 | Data-driven — CSV | `CSVDataDrivenTest` | create users from `create_users.csv` |
| 8 | Data-driven — Excel | `CreateUserTest` | create users from `APITestData.xlsx` |
| 9 | Deserialization | `GetSingleUserWithSerializationTest` | response JSON → POJO via Jackson |
| 10 | JSON Schema validation | `GetUserApiSchemaApiTest` | GET list schema, POST create schema |
| 11 | SoftAssert | `SoftAssertTest` | collect all field failures in one test; field-level validation |
| 12 | Response validation | `ResponseValidationTest` | response time SLA, Content-Type header, pagination headers, body structure |
| 13 | POJO deserialization | `ProductApiTest` | GET all products, iterate over Products[] array |
| 14 | JsonPath expressions | `ProductApiTestWithJsonPath` | filter, aggregate (min/max/avg/stddev), list of maps |
| 15 | Basic Auth | `BasicAuth` | GET with Authorization: Basic header |
| 16 | OAuth2 | `AmadeusApiTest` | client-credentials token fetch, authenticated GET |
| 17 | WireMock — mocking | `WireMockTest` | stub GET, stub POST with body match, simulate 503, simulate delay, request body matching |
| 18 | XML parsing | `XmlApiTest` | parse list of elements, attributes, single node by index, raw XmlPath |
| 19 | ReqRes full coverage | `ReqResTest` + `ReqResExtendedTest` | GET all/single, 404, POST create, PUT update, DELETE 204, login success/fail, register success/fail |
| 20 | Contacts full CRUD | `CreateContactTest` + `GetContactsTest` | login→token, GET all, POST create, GET single, PUT update, DELETE+verify 404 |

**Total: 50+ test scenarios across 20 test classes.**

---

## Learning Path

Run the `learning_suite.xml` to walk through all concepts in order:

```bash
mvn test -DsuiteXmlFile=src/test/resources/testrunners/learning_suite.xml
```

| Step | Concept | What you learn |
|------|---------|----------------|
| 1–5 | CRUD (GET/POST/PUT/PATCH/DELETE) | All HTTP verbs, AAA pattern, PATCH vs PUT |
| 6 | Negative testing | When/why to bypass RestClient; asserting 401/404/422 |
| 7–8 | Data-driven testing | CSV vs Excel vs inline @DataProvider |
| 9 | Deserialization | Jackson ObjectMapper; response → POJO |
| 10 | Schema validation | JSON Schema contract testing |
| 11 | SoftAssert | Collect all failures vs stop-at-first |
| 12 | Response validation | Response time, headers, body structure |
| 13–14 | JsonPath | JayWay JsonPath filters, aggregates, nested fields |
| 15–16 | Auth | Basic Auth, Bearer Token, OAuth2 client-credentials |
| 17 | WireMock | Mock APIs, simulate errors and delays |
| 18 | XML | GPath navigation, attribute access, indexed access |
| 19 | ReqRes | Complete endpoint coverage: create/login/register |
| 20 | Contacts API | Full CRUD with token-based auth flow |

---

## Adding a New API Under Test

1. Add the base URL to all relevant `config_<env>.properties` files.
2. Create a POJO in `src/main/java/com/qa/api/pojo/` if the endpoint has a request/response body.
3. Add a JSON schema file to `src/test/resources/schema/` for contract validation.
4. Write a test class under `src/test/java/com/qa/api/<module>/tests/`.
5. Inject `RestClient` and call the appropriate method (`getApiCall`, `postCall`, `putApiCall`, etc.).
6. Register the new test class in a TestNG XML suite file.

---

## Agent View in Claude Code

This project is set up to work with **Claude Code agents** — specialised AI sub-processes that can generate, review, and extend the framework automatically. This section explains what agents are, which ones are configured for this repo, and how to use them day-to-day.

---

### What Are Claude Code Agents?

In Claude Code, an **agent** is an isolated sub-instance of Claude that you can spawn to carry out a focused task — reading files, writing code, running the compiler — independently from the main chat session.

```
You (main session)
  └── spawns Agent A  ←── reads framework files, generates POJO + test class
  └── spawns Agent B  ←── runs code review on the same diff in parallel
        ↓
  Results returned to main session
```

Agents are used for three patterns:

| Pattern | When to use |
|---------|------------|
| **Single focused agent** | Generate one test class, review one file |
| **Parallel agents** | Run two independent tasks simultaneously (e.g. generate GoRest + ReqRes tests at the same time) |
| **Workflow (multi-agent pipeline)** | Orchestrate fan-out/fan-in across many files or APIs |

---

### Agents Configured in This Repo

#### 1. `rest-api-test-generator` *(project-specific)*

**Definition:** `.claude/agents/rest-api-test-generator.md`

This is the primary agent for this framework. It has deep knowledge of:

| Knowledge area | Detail |
|----------------|--------|
| `BaseTest` fields | All `BASE_URL_*` constants, endpoint constants, `restClient` |
| `RestClient` API | All verb methods, their signatures, and their fixed `ResponseSpecification` ranges |
| Auth patterns | `BEARER_TOKEN` via `@BeforeClass` + `ConfigManager.setProp()`, `BASIC_AUTH`, `NO_AUTH`, OAuth2 form |
| POJO conventions | Lombok `@Data @Builder @JsonInclude(NON_NULL)`, `@JsonProperty` for non-camelCase APIs |
| Negative testing rule | Must use `RestAssured.given()` directly for 401/422/503 — never `RestClient` |
| TestNG annotations | `@Test`, `@BeforeClass`, `@DataProvider`, `@Test(enabled = false)` |
| Allure annotations | `@Epic`, `@Story`, `@Severity`, `@Description`, `@Owner` |
| Logging | `ChainTestListener.log()` as the first line of every `@Test` body |
| Data-driven | Inline `@DataProvider`, `ExcelUtils.readDataFromExcel()`, `CSVReaderUtil.readDataFromCSV()` |
| Compile verification | Runs `mvn compile` with Java 11 after writing files |

**When to invoke it:**
- Adding tests for a new endpoint or API
- Extending an existing test class with new scenarios
- Creating POJOs for a new API's request/response model

---

#### 2. Built-in Agent Types

These are always available regardless of project:

| Agent type | Best for |
|------------|---------|
| `claude` | General-purpose tasks — explanation, refactoring, debugging |
| `Explore` | Fast read-only code search — "where is X defined?", "which files reference Y?" |
| `Plan` | Architecture and implementation planning before writing code |
| `claude-code-guide` | Questions about Claude Code CLI itself — hooks, settings, slash commands |
| `general-purpose` | Multi-step research and investigation across many files |

---

### How to Invoke Agents

#### Option 1 — Natural language (Claude routes automatically)

Just describe what you want. Claude will delegate to the appropriate agent:

```
"Using the rest-api-test-generator agent, add tests for this API: <paste request/response>"
```

```
"Review the current diff for bugs"          → triggers code-review skill (background agent)
"Where is SchemaValidator used?"            → triggers Explore agent
"Plan how to add a new auth type"           → triggers Plan agent
```

#### Option 2 — Explicit slash command

```
/agent rest-api-test-generator  Generate CRUD tests for the Products API
```

#### Option 3 — Parallel agents (two APIs at once)

Ask Claude to fan out:

```
"Using parallel agents: Agent 1 adds negative tests (401/422) for GoRest.
 Agent 2 adds DELETE + verify-404 tests for the Contacts API."
```

Both agents work simultaneously. Claude merges and commits the results.

---

### Practical Examples for This Framework

#### Generate a full test class from an API contract

Paste the request/response JSON and say:

```
Using the rest-api-test-generator agent, generate tests for this API:

POST https://your-api.com/v1/orders
Auth: Bearer token

Request: { "customerId": "C001", "productId": "P123", "quantity": 2 }
Response 201: { "orderId": "ORD-9876", "status": "PLACED", "totalAmount": 49.99 }
Response 422: { "error": "quantity must be >= 1" }
Response 401: unauthorized
```

The agent will produce:
- A POJO class (`Order.java`) in `src/main/java/com/qa/api/pojo/`
- A test class with `@BeforeClass` auth setup, happy-path `@Test`, and negative tests
- Assertions using `response.jsonPath().get(...)` for the fields that matter
- `JAVA_HOME=$(/usr/libexec/java_home -v 11) mvn compile -q` verification

#### Run a code review before committing

```
/review
```

or

```
"Review the current diff and report findings"
```

The code-review agent analyses staged changes across correctness, Javadoc, encoding, and test-coverage dimensions — the same review that caught 5 bugs in this repo's commit history.

#### Find where something is defined

```
"Where is ConfigManager.setProp used across all test classes?"
```

The Explore agent searches the codebase and returns file paths and line numbers without touching anything.

#### Plan before building

```
"Plan how to add XML schema validation support to this framework"
```

The Plan agent reads the existing code, proposes an approach with file-by-file steps, and waits for your approval before any code is written.

---

### Agent File Locations

```
.claude/
├── agents/
│   └── rest-api-test-generator.md   ← project-specific agent definition
└── skills/
    └── rest-api-fw/
        └── SKILL.md                  ← framework reference loaded by agents and skills
```

To **modify the agent** (add new framework knowledge, change its behaviour), edit `.claude/agents/rest-api-test-generator.md` directly. Changes take effect immediately on the next invocation — no restart required.

To **create a new agent** for this project, add a new `.md` file under `.claude/agents/` with a `name`, `description`, `model`, `tools` list, and a system prompt body.

---

### Agent vs Skill vs Plain Chat

| Mechanism | What it is | Best for |
|-----------|-----------|---------|
| **Plain chat** | You talk to Claude directly | Quick questions, small edits, explanations |
| **Skill** (`/rest-api-fw`) | A packaged instruction set Claude follows | Reference lookups, structured workflows |
| **Agent** | An isolated Claude sub-process with its own tools | Code generation, parallel tasks, long-running work that would fill the main context |
| **Agent Team** | Multiple agents coordinated by a Workflow script | Large-scale generation, parallel API coverage, fan-out/fan-in pipelines |

For generating tests: always use the **`rest-api-test-generator` agent** — it runs in isolation, keeps raw file output out of the main chat, and compile-verifies its own output before returning.

---

### Agent Teams — Concept and Application

> **Note:** This section documents the Agent Teams concept and how it maps to this framework. Agent teams are available through Claude Code's `Workflow` tool and work in this repo today.

#### What is an Agent Team?

An agent team is a group of Claude agents assigned different roles and coordinated by a script to accomplish a goal faster and more thoroughly than a single agent could alone. Each agent owns one responsibility; the coordinator script decides the order, parallelism, and how outputs feed into the next stage.

```
You (coordinator — the engineer)
  ├── Agent: POJO-Writer      → creates request/response POJOs
  ├── Agent: Test-Writer      → writes test class (depends on POJOs)
  ├── Agent: POJO-Reviewer    → reviews POJOs in parallel with Test-Writer
  └── Agent: Suite-Updater    → registers test in TestNG XML suite
```

#### Why Teams Beat a Single Agent

| Approach | Steps | Wall-clock time |
|----------|-------|----------------|
| Sequential (one agent, one task at a time) | 4 steps in series | Longest |
| Agent team (parallel where safe) | 3 steps — review overlaps with test writing | Faster |

Independent work runs simultaneously. Dependent work waits. The coordinator script expresses this with `parallel()` and `pipeline()`.

#### How It Works in This Framework

The `Workflow` tool accepts a JavaScript orchestration script. Here is a real example for adding full coverage for a new API in this repo:

```javascript
// Invoke with: Workflow({ script: `...` })

export const meta = {
  name: 'add-api-coverage',
  description: 'Generate POJOs, tests, reviewer check, and suite registration for a new API',
  phases: [
    { title: 'Generate POJOs' },
    { title: 'Write Tests + Review POJOs' },
    { title: 'Register in Suite + Compile' },
  ]
}

// ── Phase 1: POJOs first — everything else depends on them ──────────────────
phase('Generate POJOs')
const pojoResult = await agent(`
  Create ShipmentRequest.java and ShipmentResponse.java in
  src/main/java/com/qa/api/pojo/shipment/.
  Use @Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(NON_NULL).
  Use @JsonProperty for all PascalCase field names.
  Fields: shipmentId (String), carrierId (String), origin (String),
          destination (String), status (String), totalWeight (Double).
`, { label: 'pojo-writer', phase: 'Generate POJOs' })

// ── Phase 2: Write tests AND review POJOs in parallel ───────────────────────
phase('Write Tests + Review POJOs')
const [testResult, reviewResult] = await parallel([

  () => agent(`
    Create ShipmentTest.java in src/test/java/com/qa/api/shipment/tests/.
    Extend BaseTest. Add @BeforeClass to inject bearer token via ConfigManager.
    Write three @Test methods:
      1. getShipmentTest        — GET /shipments, assert 200, use BEARER_TOKEN
      2. getShipmentNotFoundTest — GET /shipments/INVALID, use RestAssured.given()
                                   directly (NOT RestClient), assert 404
      3. createShipmentTest     — POST with ShipmentRequest POJO, assert 201,
                                   assert shipmentId not null
    Follow the same style as UpdateUserTest.java.
    ChainTestListener.log() must be the first line of every @Test method.
  `, { label: 'test-writer', phase: 'Write Tests + Review POJOs' }),

  () => agent(`
    Review src/main/java/com/qa/api/pojo/shipment/ for correctness.
    Check: @JsonProperty present on every field, @JsonInclude(NON_NULL) at class level,
    all four Lombok annotations present (@Data @Builder @NoArgsConstructor @AllArgsConstructor),
    no magic strings, no raw types.
    Report any issues found (or "LGTM" if clean).
  `, { label: 'pojo-reviewer', phase: 'Write Tests + Review POJOs' })

])

// ── Phase 3: Register test in suite, then compile ───────────────────────────
phase('Register in Suite + Compile')
await agent(`
  Add ShipmentTest to src/test/resources/testrunners/testng_regression.xml.
  Follow the exact same <class name="..."> pattern used by the existing entries.
  Then run: JAVA_HOME=$(/usr/libexec/java_home -v 11) mvn compile -q
  Report compile result (clean or error output).
`, { label: 'suite-updater', phase: 'Register in Suite + Compile' })

return { pojoResult, testResult, reviewResult }
```

#### Timeline Visualised

```
Sequential (no team):
  |── POJO-Writer ──|── Test-Writer ──|── POJO-Reviewer ──|── Suite-Updater ──|

Agent Team (parallel where safe):
  |── POJO-Writer ──|── Test-Writer ────────|── Suite-Updater ──|
                    |── POJO-Reviewer ──|
                         ↑ these two overlap
```

#### Key Rules for Agent Teams

| Rule | Reason |
|------|--------|
| Agents that share file dependencies run sequentially | Prevent race conditions on the same file |
| Agents doing independent work run in `parallel()` | Safe overlap — different files, no shared state |
| Never let an agent push or merge | Human-only — coordinator reviews first |
| Each agent gets one clear responsibility | Focused context = better output quality |
| Compile-verify as the last step | Catches import errors and type mismatches across all generated files |

#### When to Use Agent Teams in This Project

| Scenario | Team pattern |
|----------|-------------|
| New API: POJO + test + suite registration | Pipeline (POJO → Tests ‖ Review → Register) |
| Add tests for multiple APIs at once | `parallel()` fan-out, one test-writer per API |
| Large diff: review across multiple dimensions | `parallel()` with correctness / security / coverage reviewers |
| Audit entire test suite for coverage gaps | Fan-out one finder agent per package, synthesize gaps |

#### Current Limitation Note

Custom subagent types (e.g. `subagent_type: "rest-api-test-generator"`) require a model that matches the team's allowed list. In this repo, **use `subagent_type: "fork"`** inside Workflow scripts — forks always inherit the session model (`tm-sonnet-4-6`) and bypass this restriction entirely. The `Workflow` tool itself is unaffected and works normally.

---

### Agent View — Monitoring and Observing Agents

> **Note:** This section explains how to observe, communicate with, and control agents while they run — the operational side of working with agents in Claude Code.

#### What is Agent View?

Agent View is how you see what is happening inside running agents — their status, progress, output, and whether they succeeded or failed. Claude Code provides several mechanisms depending on whether you spawned a single agent, a multi-agent workflow, or a background task.

```
You (main session)
  ├── spawned Agent A  →  running... how do I know when it's done?
  ├── spawned Agent B  →  failed?   how do I see why?
  └── spawned Workflow →  3 phases, 8 agents... which ones finished?
```

---

#### Observability Tools

| Tool / Command | What it shows |
|----------------|--------------|
| `ListAgents` | All agents currently reachable — in-process subagents, other local sessions |
| `/workflows` | Live progress tree for running Workflow scripts — phase names, agent labels, pass/fail per agent |
| `TaskOutput` | Fetch stdout/stderr from a background Bash or remote task |
| `Monitor` | Stream live events from a long-running process line by line |
| `SendMessage` | Send a message to a named running agent |
| `TaskStop` | Kill a running background agent or task |

---

#### Lifecycle of an Agent — What You See

```
1. You invoke: Agent({ prompt: "...", subagent_type: "fork" })
       ↓
2. Claude Code launches the agent in the background
   → Returns: agentId (internal), output_file path
       ↓
3. Agent runs (reads files, writes code, compiles)
   → You see: "[agent is running]" in UI; you can do other work
       ↓
4. Agent finishes
   → A <task-notification> arrives automatically with:
       status: completed | failed
       result: the agent's final text output
       usage: tokens used, tool calls made, duration
       ↓
5. You read the result and decide what to do next
   (review the diff, commit, ask for changes)
```

---

#### Workflow Progress View (`/workflows`)

When a `Workflow` script is running, `/workflows` shows a live tree:

```
▸ add-api-coverage  [running — 32s]
  ✔ Generate POJOs
    ✔ pojo-writer          [done, 8s]
  ⟳ Write Tests + Review POJOs
    ✔ test-writer          [done, 14s]
    ⟳ pojo-reviewer        [running...]
  ○ Register in Suite + Compile
    ○ suite-updater        [waiting]
```

Each row is one `agent()` call in the script. `✔` = completed, `⟳` = in progress, `○` = not yet started, `✗` = failed. The phase grouping comes from the `phase()` calls and the `label:` option on each `agent()` call.

---

#### Communicating with a Running Agent

If an agent is still running and you need to redirect it, use `SendMessage`:

```
ListAgents()           ← find the agent's name
SendMessage({
  to: "pojo-writer",
  message: "Also add a field: trackingNumber (String)"
})
```

The message is queued and delivered at the agent's next tool round. This works for in-process subagents and other Claude Code sessions on the same machine.

---

#### Reading Agent Output

Every spawned agent writes its result to an `output_file`. **Do not `cat` or `Read` this file directly** — it is a full JSONL transcript and will overflow your context window. Instead:

- For **completed agents**: the result is in the `<task-notification>` automatically delivered to your session.
- For **Workflow agents**: read `.output` journals under the session transcript directory only when debugging a failure — and only the specific `agent-<id>.jsonl` for the failing agent, not the entire transcript.
- For **background Bash tasks**: `TaskOutput({ task_id: "..." })` fetches stdout/stderr safely.

---

#### Stopping an Agent

```
TaskStop({ task_id: "agent-name" })   ← stop by name
TaskStop({ task_id: "abc123" })       ← stop by agentId
```

Use this when:
- An agent is taking too long and you want to redirect it
- A Workflow phase produced bad output and continuing is pointless
- You realise the prompt was wrong mid-run

---

#### Practical Agent View Examples for This Repo

**Check if a previously spawned test-generator is still running:**
```
ListAgents()
→ shows: rest-api-test-generator [running] or no results if it completed
```

**Watch a long Maven test suite run line by line:**
```
Monitor({
  command: "JAVA_HOME=$(/usr/libexec/java_home -v 11) mvn test -Dtest=SaveFreightInvoiceTest 2>&1 | grep --line-buffered -E 'PASSED|FAILED|ERROR|Tests run'",
  description: "SaveFreightInvoiceTest live results"
})
→ Each matching line fires a notification as it happens
```

**Track a Workflow running coverage generation across 5 APIs:**
```
/workflows
→ see which API's test-writer finished, which is still running, which failed
```

**Kill a stuck agent and restart with a corrected prompt:**
```
TaskStop({ task_id: "test-writer" })
Agent({ subagent_type: "fork", prompt: "corrected prompt here..." })
```

---

#### Agent View vs Agent Teams — How They Work Together

```
Agent Teams  →  how you DESIGN and ORCHESTRATE work across agents
Agent View   →  how you OBSERVE and CONTROL agents while they run
```

They are complementary. You design a team in a Workflow script (Agent Teams), then use Agent View tools (`/workflows`, `ListAgents`, `SendMessage`, `TaskStop`) to monitor its execution and intervene if needed.

| You want to... | Use |
|----------------|-----|
| See which agents are running | `ListAgents` |
| Watch a workflow's live progress | `/workflows` |
| Get an agent's final output | Read the `<task-notification>` result |
| Send mid-flight instructions | `SendMessage` |
| Stop a misbehaving agent | `TaskStop` |
| Watch live test output line by line | `Monitor` |
