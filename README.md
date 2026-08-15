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
Jackson + Lombok POJOs (`User`, `Products`, `Credentials`) are used for both request body construction and response deserialization. `ObjectMapperUtils` handles JSON↔POJO conversion.

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
