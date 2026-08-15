package com.qa.api.assertions;

import com.qa.api.base.BaseTest;
import com.qa.api.constants.AppConstants;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import io.qameta.allure.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * LEARNING: Beyond status codes — what else to validate in an API response:
 *   1. Response time (performance SLA)
 *   2. Content-Type header (contract)
 *   3. Specific headers (rate-limit, correlation-id, etc.)
 *   4. Response body structure
 *   5. Status line text
 */
@Epic("Response Validation Patterns")
@Story("Validates response time, headers, and body structure")
public class ResponseValidationTest extends BaseTest {

    @BeforeClass
    public void setupToken() {
        ConfigManager.setProp("bearerToken", "11fae3b4c82533f3b3ddb8152da966d8c66cd6f9bc182f7bd64d3109f55bb22f");
    }

    @Description("Assert response time is within the allowed SLA threshold")
    @Severity(SeverityLevel.NORMAL)
    @Test
    public void responseTimeTest() {
        Response response = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        long responseTimeMs = response.time();
        System.out.println("Response time: " + responseTimeMs + "ms  (SLA: " + AppConstants.API_TIME_OUT + "ms)");

        Assert.assertTrue(responseTimeMs < AppConstants.API_TIME_OUT,
                "API is too slow: responded in " + responseTimeMs + "ms, SLA = " + AppConstants.API_TIME_OUT + "ms");
    }

    @Description("Validate Content-Type header is application/json")
    @Severity(SeverityLevel.NORMAL)
    @Test
    public void contentTypeHeaderTest() {
        Response response = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        String contentType = response.getHeader("Content-Type");
        System.out.println("Content-Type: " + contentType);

        Assert.assertNotNull(contentType, "Content-Type header should be present");
        Assert.assertTrue(contentType.contains("application/json"),
                "Expected JSON Content-Type but got: " + contentType);
    }

    @Description("Print all response headers — useful for discovering rate-limit and pagination headers")
    @Severity(SeverityLevel.NORMAL)
    @Test
    public void printAllResponseHeadersTest() {
        Response response = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        System.out.println("=== All Response Headers ===");
        response.getHeaders().forEach(header ->
                System.out.println(header.getName() + " : " + header.getValue()));

        // GoRest returns pagination headers — assert they are present
        String totalCount = response.getHeader("X-Pagination-Total");
        System.out.println("X-Pagination-Total: " + totalCount);
        Assert.assertNotNull(totalCount, "Pagination header X-Pagination-Total should be present");
    }

    @Description("Assert response body is a non-empty JSON array")
    @Severity(SeverityLevel.NORMAL)
    @Test
    public void responseBodyStructureTest() {
        Response response = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        String body = response.getBody().asString();
        Assert.assertFalse(body.isEmpty(), "Response body must not be empty");
        Assert.assertTrue(body.startsWith("["), "Expected JSON array (starts with '[')");
        Assert.assertTrue(body.endsWith("]"), "Expected JSON array (ends with ']')");
    }

    @Description("Validate status line contains the expected HTTP status text")
    @Severity(SeverityLevel.NORMAL)
    @Test
    public void statusLineValidationTest() {
        Response response = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        System.out.println("Status line: " + response.statusLine());
        Assert.assertTrue(response.statusLine().contains("200"),
                "Status line should contain '200', got: " + response.statusLine());
    }
}
