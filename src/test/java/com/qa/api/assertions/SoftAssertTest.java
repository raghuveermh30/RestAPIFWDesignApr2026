package com.qa.api.assertions;

import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import io.qameta.allure.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

/**
 * LEARNING: Hard Assert vs Soft Assert
 *
 * Hard Assert (Assert.assertEquals): stops the test immediately on first failure.
 * Soft Assert (SoftAssert): collects ALL failures and reports them together at softAssert.assertAll().
 *
 * Use Soft Assert when you want to validate multiple independent fields in a single test
 * and see ALL failures at once rather than stopping at the first one.
 */
@Epic("Assertion Patterns")
@Story("Demonstrates SoftAssert for collecting multiple assertion failures")
public class SoftAssertTest extends BaseTest {

    @BeforeClass
    public void setupToken() {
        ConfigManager.setProp("bearerToken", "11fae3b4c82533f3b3ddb8152da966d8c66cd6f9bc182f7bd64d3109f55bb22f");
    }

    @Description("Validate multiple response attributes using SoftAssert — all failures reported together")
    @Severity(SeverityLevel.NORMAL)
    @Test
    public void getAllUsersWithSoftAssertTest() {
        Response response = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        SoftAssert softAssert = new SoftAssert();

        softAssert.assertEquals(response.getStatusCode(), 200,
                "Status code mismatch");
        softAssert.assertTrue(response.time() < 5000,
                "Response time exceeded 5s: actual = " + response.time() + "ms");
        softAssert.assertNotNull(response.jsonPath().getList("$"),
                "Response body should be a non-null list");
        softAssert.assertTrue(response.contentType().contains("application/json"),
                "Content-Type should be application/json");
        softAssert.assertTrue(response.getBody().asString().startsWith("["),
                "Response should be a JSON array starting with '['");

        // All failures surface here — test continues past individual failing asserts above
        softAssert.assertAll();
    }

    @Description("Validate each user object field with SoftAssert")
    @Severity(SeverityLevel.NORMAL)
    @Test
    public void validateUserFieldsWithSoftAssertTest() {
        Response response = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        SoftAssert softAssert = new SoftAssert();

        // Extract first user's fields from array
        Integer firstId = response.jsonPath().get("[0].id");
        String firstName = response.jsonPath().get("[0].name");
        String firstEmail = response.jsonPath().get("[0].email");
        String firstGender = response.jsonPath().get("[0].gender");
        String firstStatus = response.jsonPath().get("[0].status");

        softAssert.assertNotNull(firstId, "User id should not be null");
        softAssert.assertNotNull(firstName, "User name should not be null");
        softAssert.assertNotNull(firstEmail, "User email should not be null");
        softAssert.assertTrue(firstEmail.contains("@"), "Email should contain '@'");
        softAssert.assertTrue("male".equals(firstGender) || "female".equals(firstGender),
                "Gender should be male or female, got: " + firstGender);
        softAssert.assertTrue("active".equals(firstStatus) || "inactive".equals(firstStatus),
                "Status should be active or inactive, got: " + firstStatus);

        softAssert.assertAll();
    }
}
