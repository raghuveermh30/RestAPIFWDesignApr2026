# REST API Automation Framework — Skill Reference

## Overview

Java + Maven framework built on **Rest Assured 5.5** and **TestNG 7.10**.
Every test extends `BaseTest`, which wires `RestClient` and loads config at suite startup.
There is no page-object or API-object layer — tests call `RestClient` methods directly.

```
Test class
  └── extends BaseTest  (RestClient + base URL constants)
        └── calls RestClient  (generic HTTP wrapper, one method per verb)
              └── uses RestAssured  (sends HTTP, validates ResponseSpecification)
```

---

## 1. Adding a New Test

### Checklist
1. Create a test class in the appropriate package under `src/test/java/com/qa/api/`.
2. Extend `BaseTest`.
3. Set auth token at `@BeforeClass` if needed via `ConfigManager.setProp("bearerToken", token)`.
4. Call `restClient.<verb>ApiCall(...)` for happy-path tests; call `RestAssured.given()` directly for negative tests that expect error codes (401, 422, 503).
5. Add the class to the relevant TestNG XML suite under `src/test/resources/testrunners/`.

### Package conventions

| API under test | Package |
|---|---|
| GoRest users | `com.qa.api.gorest.tests` |
| Contacts | `com.qa.api.contacts.test` |
| ReqRes | `com.qa.api.reqres.tests` |
| FakeStore Products | `com.qa.api.products` |
| Amadeus OAuth2 | `com.qa.api.amedus.test` |
| Basic Auth | `com.qa.api.basicauth` |
| WireMock stubs | `com.qa.api.wiremock` |
| XML responses | `com.qa.api.xml` |
| Assertion patterns | `com.qa.api.assertions` |
| Schema tests | `com.qa.api.scematest` |

---

## 2. RestClient Methods

All methods live in `src/main/java/com/qa/api/client/RestClient.java`.

| Method | Verb | Fixed ResponseSpec |
|---|---|---|
| `getApiCall(...)` | GET | 200 or 404 |
| `postCall(baseUrl, endpoint, body, ...)` | POST (POJO or String body) | 200 or 201 |
| `postCall(baseUrl, endpoint, File, ...)` | POST (file body) | 200 or 201 |
| `postApiCall(baseUrl, endpoint, clientId, clientSecret, grantType, ...)` | POST (OAuth2 form) | none (raw) |
| `putApiCall(...)` | PUT | 200 |
| `patchApiCall(...)` | PATCH | 200 |
| `deleteApiCall(...)` | DELETE | 204 or 404 |

> **Rule:** If your test needs a status code outside those ranges (e.g. 401, 422, 503), skip `RestClient` and call `RestAssured.given()` directly — see `NegativeGoRestTest` for the pattern.

### Minimal GET example
```java
Response response = restClient.getApiCall(
    BASE_URL_GOREST,   // base URI
    GOREST_END_POINT,  // path
    null,              // queryParams (Map<String,String> or null)
    null,              // pathParams  (Map<String,String> or null)
    AuthType.BEARER_TOKEN,
    ContentType.JSON
);
```

### Minimal POST example
```java
User user = new User(null, "Raghu", StringUtils.getRandomEmailId(), "male", "active");
Response response = restClient.postCall(
    BASE_URL_GOREST, GOREST_END_POINT, user,
    null, null, AuthType.BEARER_TOKEN, ContentType.JSON
);
```

---

## 3. Auth Patterns

Controlled by the `AuthType` enum (`BEARER_TOKEN`, `BASIC_AUTH`, `API_KEY`, `NO_AUTH`).
`RestClient.setupRequest()` reads credentials from `ConfigManager`.

### Bearer token (GoRest, Contacts)
```java
@BeforeClass
public void setToken() {
    ConfigManager.setProp("bearerToken", "<token>");
}
// then pass AuthType.BEARER_TOKEN to any RestClient call
```

### Basic Auth
Credentials are read from `basicUserName` / `basicPassword` in the properties file.
Pass `AuthType.BASIC_AUTH` — the header is built automatically.

### OAuth2 client credentials (Amadeus)
```java
Response tokenResponse = restClient.postApiCall(
    BASE_URL_OAUTH2_AMADEUS, AMADEUS_OAUTH2_TOKEN_ENDPOINT,
    ConfigManager.getProp("clientId"),
    ConfigManager.getProp("clientSecret"),
    ConfigManager.getProp("grant_type"),
    ContentType.URLENC
);
String token = tokenResponse.jsonPath().getString("access_token");
ConfigManager.setProp("bearerToken", token);
```

---

## 4. Data-Driven Patterns

### Inline `@DataProvider`
```java
@DataProvider
public Object[][] getUserData() {
    return new Object[][]{
        {"Raghu", "male", "active"},
        {"Priya", "female", "inactive"}
    };
}
@Test(dataProvider = "getUserData")
public void createUserTest(String name, String gender, String status) { ... }
```

### Excel (`APITestData.xlsx`, sheet `createuser`)
```java
@DataProvider
public Object[][] getUserExcelData() {
    return ExcelUtils.readDataFromExcel(AppConstants.CREATE_USER_SHEET_NAME);
}
@Test(dataProvider = "getUserExcelData")
public void createUserTestFromExcel(String name, String gender, String status) { ... }
```

### CSV (`create_users.csv`)
`CSVReaderUtil.readCSV()` skips the header row automatically.
See `CSVDataDrivenTest` for the full pattern.

---

## 5. Negative Testing

Use raw `RestAssured.given()` — never `RestClient` — when you need to assert on error codes outside the baked-in `ResponseSpecification` ranges.

```java
@Test
public void unauthorizedTest() {
    Response response = RestAssured.given().log().all()
        .baseUri(BASE_URL_GOREST)
        .contentType(ContentType.JSON)
        .get(GOREST_END_POINT)
        .then().log().all().extract().response();

    Assert.assertEquals(response.getStatusCode(), 401);
}
```

Common error codes tested: **401** (no/invalid token), **404** (resource not found), **422** (validation failure / duplicate email), **503** (service unavailable — WireMock stub).

---

## 6. JSON Schema Validation

Schemas live in `src/test/resources/schema/`.

```java
boolean valid = SchemaValidator.validateSchema(response, "get-user-schema.json");
Assert.assertTrue(valid, "Schema validation failed");
```

`SchemaValidator` wraps Rest Assured's built-in JSON Schema Validator.

---

## 7. Assertion Styles

### Hard Assert — stops on first failure
```java
Assert.assertEquals(response.getStatusCode(), 200);
Assert.assertNotNull(response.jsonPath().get("id"));
```

### SoftAssert — collects all failures before reporting
```java
SoftAssert softAssert = new SoftAssert();
softAssert.assertEquals(response.getStatusCode(), 200, "Status mismatch");
softAssert.assertTrue(response.time() < 5000, "Too slow");
softAssert.assertNotNull(response.jsonPath().getList("$"), "Body null");
softAssert.assertAll(); // required — throws here if any checks failed
```

Use `SoftAssert` when validating multiple independent fields in one test — see `SoftAssertTest`.

---

## 8. WireMock — API Mocking

Two embedded servers run in the same test run — they must use different ports.

| Test class | Port |
|---|---|
| `WireMockTest` | 8089 |
| `XmlApiTest` | 8091 |

```java
@BeforeClass
public void startWireMock() {
    wireMockServer = new WireMockServer(wireMockConfig().port(8089));
    wireMockServer.start();
    WireMock.configureFor("localhost", 8089);
}

@Test
public void stubGetTest() {
    stubFor(get(urlEqualTo("/api/mock/users"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("[{\"id\": 1, \"name\": \"Mock User\"}]")));

    Response response = RestAssured.given()
        .baseUri("http://localhost:8089")
        .get("/api/mock/users")
        .then().extract().response();

    Assert.assertEquals(response.getStatusCode(), 200);
    verify(1, getRequestedFor(urlEqualTo("/api/mock/users")));
}

@AfterClass
public void stopWireMock() {
    if (wireMockServer != null && wireMockServer.isRunning()) wireMockServer.stop();
}
```

---

## 9. XML / XmlPath

Requires a WireMock stub (or real API) returning `Content-Type: application/xml`.

```java
// Single value via GPath
String circuitName = XmlPathUtil.read(response, "MRData.CircuitTable.Circuit[0].circuitName");

// List of values
List<String> names = XmlPathUtil.readList(response, "MRData.CircuitTable.Circuit.circuitName");
```

---

## 10. POJO / Deserialization

POJOs are in `src/main/java/com/qa/api/pojo/` — all annotated with `@JsonInclude(NON_NULL)` and Lombok.

```java
// Via JsonUtil wrapper
User user = JsonUtil.deserialize(response, User.class);

// Via ObjectMapperUtils
User user = ObjectMapperUtils.toObject(response.asString(), User.class);
```

---

## 11. Utilities Reference

| Utility | Key Method | Use |
|---|---|---|
| `StringUtils` | `getRandomEmailId()` | Unique email per run (timestamp-based) |
| `StringUtils` | `getRandomName()` | Unique name per run |
| `ExcelUtils` | `readDataFromExcel(sheetName)` | `Object[][]` from `.xlsx` |
| `CSVReaderUtil` | `readCSV(path)` | `Object[][]` from `.csv`, skips header |
| `JsonPathValidatorUtil` | `read(response, path)` | Single value via JayWay JsonPath |
| `JsonPathValidatorUtil` | `readList(response, path)` | List via JayWay JsonPath |
| `XmlPathUtil` | `read(response, gpath)` | Single value via GPath |
| `XmlPathUtil` | `readList(response, gpath)` | List via GPath |
| `SchemaValidator` | `validateSchema(response, file)` | JSON schema contract check |
| `ConfigManager` | `getProp(key)` / `setProp(key, v)` | Read/write runtime config |

---

## 12. Configuration

Environment is selected at Maven runtime (default: `qa`).

```bash
mvn test -Denv=qa   # or dev | uat | stage | prod
```

Properties files: `src/test/resources/config_<env>.properties`

| Key | Purpose |
|---|---|
| `baseurl.gorest` | GoRest base URL |
| `bearerToken` | GoRest token placeholder — tests override via `ConfigManager.setProp` |
| `basicUserName` / `basicPassword` | Basic auth credentials |
| `clientId` / `clientSecret` / `grant_type` | Amadeus OAuth2 |
| `apikey` | API key auth |

---

## 13. Run Commands

```bash
# Default suite (configured in pom.xml)
mvn test

# Specific TestNG XML suite
mvn test -DsuiteXmlFile=src/test/resources/testrunners/learning_suite.xml

# Specific environment
mvn test -Denv=uat

# Single class
mvn test -Dtest=CreateUserTest

# Single method
mvn test -Dtest=CreateUserTest#createUserTest

# Suite + environment
mvn test -DsuiteXmlFile=src/test/resources/testrunners/gorest_regression.xml -Denv=qa

# Generate Allure report after a run
mvn allure:report
```

---

## 14. Available Suites

| Suite file | Scope |
|---|---|
| `testng_sanity.xml` | Quick sanity: UpdateUser, Product, Schema, BasicAuth |
| `learning_suite.xml` | All 20 concept groups in learning order |
| `gorest_regression.xml` | Full GoRest CRUD regression |
| `products_regression.xml` | Products API regression |
| `schema_regression.xml` | Schema contract regression |
| `testng_regression.xml` | Full regression |

---

## 15. Reporting

- **Allure** — `AllureRestAssured` filter attached in `BaseTest.initialSetup()`. Annotate tests with `@Step`, `@Epic`, `@Story`, `@Description`, `@Severity`. Results land in `target/allure-results/`.
- **ChainTest** — `@Listeners(ChainTestListener.class)` is on `BaseTest`. Use `ChainTestListener.log("...")` inside test methods for step-level logging. Reports land in `target/chaintest/`.
