package com.qa.api.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.qa.api.base.BaseTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * LEARNING: WireMock — API Mocking
 *
 * Why mock?
 *   - Test your code when the real API is unavailable, too slow, or costs money per call.
 *   - Simulate error scenarios (503, timeouts) that are hard to trigger on live APIs.
 *   - Run tests without network access (CI offline mode).
 *
 * How WireMock works:
 *   1. Start an embedded HTTP server on a local port.
 *   2. Register stubs (URL + expected request → canned response).
 *   3. Point your HTTP client at http://localhost:<port> instead of the real API.
 *   4. After the call, verify() how many times a stub was hit.
 */
public class WireMockTest extends BaseTest {

    private static final int MOCK_PORT = 8089;
    private static final String MOCK_BASE = "http://localhost:" + MOCK_PORT;

    private WireMockServer wireMockServer;

    @BeforeClass
    public void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().port(MOCK_PORT));
        wireMockServer.start();
        WireMock.configureFor("localhost", MOCK_PORT);
    }

    @AfterClass
    public void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    public void mockGetUsersResponseTest() {
        // Register stub: GET /api/mock/users → 200 JSON array
        stubFor(get(urlEqualTo("/api/mock/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\": 1, \"name\": \"Mock User\", \"email\": \"mock@test.com\"}]")));

        Response response = RestAssured.given().log().all()
                .baseUri(MOCK_BASE)
                .get("/api/mock/users")
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 200);
        Assert.assertEquals(response.jsonPath().getString("[0].name"), "Mock User");
        Assert.assertEquals(response.jsonPath().getString("[0].email"), "mock@test.com");

        // Verify the stub was hit exactly once
        verify(1, getRequestedFor(urlEqualTo("/api/mock/users")));
    }

    @Test
    public void mockPostCreateUserResponseTest() {
        // Stub: POST body must contain "name" → 201 with created resource
        stubFor(post(urlEqualTo("/api/mock/users"))
                .withRequestBody(containing("\"name\""))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": 101, \"name\": \"New Mock User\", \"status\": \"active\"}")));

        String requestBody = "{\"name\": \"New Mock User\", \"email\": \"new@test.com\", \"gender\": \"male\", \"status\": \"active\"}";

        Response response = RestAssured.given().log().all()
                .baseUri(MOCK_BASE)
                .contentType(ContentType.JSON)
                .body(requestBody)
                .post("/api/mock/users")
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 201);
        Assert.assertEquals((int) response.jsonPath().get("id"), 101);
        Assert.assertEquals(response.jsonPath().getString("name"), "New Mock User");
    }

    @Test
    public void mockServiceUnavailableScenarioTest() {
        // Simulate a downstream service being down (503)
        stubFor(get(urlEqualTo("/api/mock/payment-service"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Service Unavailable\", \"retryAfter\": 30}")));

        Response response = RestAssured.given().log().all()
                .baseUri(MOCK_BASE)
                .get("/api/mock/payment-service")
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 503,
                "Mocked service should return 503");
        Assert.assertEquals(response.jsonPath().getString("error"), "Service Unavailable");
        Assert.assertEquals((int) response.jsonPath().get("retryAfter"), 30);
    }

    @Test
    public void mockNetworkDelayTest() {
        // Simulate a slow API — useful to test client timeout behavior
        stubFor(get(urlEqualTo("/api/mock/slow-endpoint"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(600)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"slow but successful\"}")));

        long start = System.currentTimeMillis();

        Response response = RestAssured.given().log().all()
                .baseUri(MOCK_BASE)
                .get("/api/mock/slow-endpoint")
                .then().log().all().extract().response();

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Elapsed time: " + elapsed + "ms");

        Assert.assertEquals(response.getStatusCode(), 200);
        Assert.assertTrue(elapsed >= 600,
                "Response should have been delayed by at least 600ms, actual: " + elapsed + "ms");
    }

    @Test
    public void mockRequestBodyMatchingTest() {
        // Stub that only fires when the body contains a specific JSON field value
        stubFor(post(urlEqualTo("/api/mock/login"))
                .withRequestBody(matchingJsonPath("$.email", equalTo("admin@test.com")))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\": \"mock-jwt-token-12345\"}")));

        String loginBody = "{\"email\": \"admin@test.com\", \"password\": \"secret\"}";

        Response response = RestAssured.given().log().all()
                .baseUri(MOCK_BASE)
                .contentType(ContentType.JSON)
                .body(loginBody)
                .post("/api/mock/login")
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 200);
        Assert.assertEquals(response.jsonPath().getString("token"), "mock-jwt-token-12345");
    }
}
