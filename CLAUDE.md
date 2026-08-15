# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Compile the project
mvn compile

# Run the default sanity suite (configured in pom.xml)
mvn test

# Run a specific TestNG XML suite
mvn test -DsuiteXmlFile=src/test/resources/testrunners/gorest_regression.xml
mvn test -DsuiteXmlFile=src/test/resources/testrunners/learning_suite.xml

# Run against a specific environment (default: qa)
mvn test -Denv=qa
mvn test -Denv=dev
mvn test -Denv=uat

# Run a single test class
mvn test -Dtest=GetUserTest
mvn test -Dtest=WireMockTest

# Run a single test method
mvn test -Dtest=CreateUserTest#createUserTest

# Combine suite + environment
mvn test -DsuiteXmlFile=src/test/resources/testrunners/gorest_regression.xml -Denv=qa

# Generate Allure report (after running tests)
mvn allure:report
```

## Available TestNG Suites

| Suite file | Scope |
|------------|-------|
| `testng_sanity.xml` | Quick sanity: UpdateUser, Product, Schema, BasicAuth |
| `learning_suite.xml` | All 20 concept groups in learning order |
| `gorest_regression.xml` | Full GoRest CRUD regression |
| `products_regression.xml` | Products API regression |
| `schema_regression.xml` | Schema contract regression |
| `testng_regression.xml` | Full regression |

## Architecture

### Request flow

Every test extends `BaseTest`, which initialises a `RestClient` instance and loads the base URLs and `AllureRestAssured` filter in `@BeforeSuite`. Test methods call `RestClient` methods directly — there is no intermediate "page object" or "API object" layer.

```
Test class
  └── extends BaseTest  (holds RestClient + base URL constants)
        └── calls RestClient  (generic HTTP wrapper — one method per verb)
              └── uses RestAssured  (sends HTTP, validates response spec)
```

### Key design decisions

**`RestClient` response specs are baked in.** Each method enforces a fixed `ResponseSpecification` (e.g. `getApiCall` accepts 200 or 404; `deleteApiCall` accepts 204 or 404). For tests that assert on error codes outside those ranges (401, 422, 503) you must call `RestAssured.given()` directly — do not try to use RestClient for negative tests.

**`ConfigManager` is a static singleton.** It reads `config_<env>.properties` once at class-load time using the `env` system property. Auth tokens that are generated at runtime (OAuth2, Contacts login) are injected back via `ConfigManager.setProp("bearerToken", token)` so subsequent `RestClient` calls pick them up automatically.

**Auth is selected per-call via `AuthType` enum.** `RestClient.setupRequest()` switches on `AuthType` (BEARER_TOKEN, BASIC_AUTH, API_KEY, NO_AUTH) and pulls credentials from `ConfigManager` properties.

### `src/main/java` — framework layer

| Package / file | Purpose |
|----------------|---------|
| `client/RestClient` | All HTTP verbs: `getApiCall`, `postCall` (body or File), `putApiCall`, `patchApiCall`, `deleteApiCall`, `postApiCall` (OAuth2 form) |
| `constants/AuthType` | Enum used by every RestClient call to select the auth header |
| `constants/AppConstants` | `API_TIME_OUT` (5000 ms), Excel sheet name, CSV path constant |
| `manager/ConfigManager` | Reads `config_<env>.properties`; `getProp`/`setProp` at runtime |
| `pojo/` | Lombok + Jackson POJOs: `User`, `Products`, `Contact`, `Credentials` — all use `@JsonInclude(NON_NULL)` |
| `schema/SchemaValidator` | Wraps Rest Assured JSON schema validation; returns `boolean` |
| `utils/ExcelUtils` | Reads `.xlsx` via Apache POI; returns `Object[][]` for TestNG `@DataProvider` |
| `utils/CSVReaderUtil` | Reads `.csv`; skips header row; returns `Object[][]` |
| `utils/JsonPathValidatorUtil` | JayWay JsonPath: `read`, `readList`, `readListOfMaps` |
| `utils/XmlPathUtil` | Rest Assured XmlPath GPath helpers: `read`, `readList`, `getXmlPath` |
| `utils/JsonUtil` | Jackson `deserialize(Response, Class<T>)` |
| `utils/StringUtils` | `getRandomEmailId()`, `getRandomName()` using `System.currentTimeMillis()` |

### `src/test/java` — test layer

Test packages map to the API under test:

| Package | API | Auth |
|---------|-----|------|
| `gorest/tests/` | `https://gorest.co.in/public/v2/users` | Bearer token |
| `contacts/test/` | `https://thinking-tester-contact-list.herokuapp.com` | Bearer (login first) |
| `reqres/tests/` | `https://reqres.in` | None |
| `products/` | `https://fakestoreapi.com/products` | None |
| `amedus/test/` | `https://test.api.amadeus.com` | OAuth2 client credentials |
| `basicauth/` | `https://the-internet.herokuapp.com` | Basic Auth |
| `wiremock/` | `localhost:8089` (embedded) | None |
| `xml/` | `localhost:8091` (embedded WireMock) | None |
| `assertions/` | GoRest | Bearer token |
| `scematest/` | GoRest | Bearer token |

### Test data

| Source | Location | Used by |
|--------|----------|---------|
| Excel | `src/test/resources/testdata/APITestData.xlsx` (sheet: `createuser`) | `CreateUserTest.createUserTestFromExcel` |
| CSV | `src/test/resources/testdata/create_users.csv` | `CSVDataDrivenTest` |
| JSON body file | `src/test/resources/jsons/user.json` | `CreateUserTest.createUserWithFileTest` (disabled) |
| JSON schemas | `src/test/resources/schema/*.json` | `GetUserApiSchemaApiTest`, `SchemaValidator` |

### Environment config

Each `config_<env>.properties` file provides: `baseurl`, `baseurl.gorest`, `baseurl.reqres`, `bearerToken`, `basicUserName`, `basicPassword`, `clientId`, `clientSecret`, `grant_type`, `apikey`.

The GoRest bearer token in config files is a placeholder — tests that use GoRest set the token at `@BeforeClass` via `ConfigManager.setProp("bearerToken", "<token>")`.

### WireMock ports

Two embedded WireMock servers are used — they must not conflict:
- `WireMockTest`: port **8089**
- `XmlApiTest`: port **8091**

Each starts in `@BeforeClass` and stops in `@AfterClass`.
