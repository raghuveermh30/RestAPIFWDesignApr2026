package com.qa.api.gorest.tests;

import com.qa.api.base.BaseTest;
import com.qa.api.pojo.User;
import com.qa.api.utils.StringUtils;
import io.qameta.allure.*;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * LEARNING: Negative Testing
 *
 * RestClient always validates the status code via a ResponseSpecification.
 * For negative scenarios where we EXPECT error codes (401, 404, 422),
 * we bypass RestClient and call RestAssured directly so we can assert
 * on the exact error response without triggering an assertion failure.
 *
 * This teaches two key concepts:
 *   1. When and why to use raw RestAssured instead of a wrapper.
 *   2. How to validate API error contracts.
 */
@Epic("GoRest Negative Tests")
@Story("Validates API error responses: 401, 404, 422")
public class NegativeGoRestTest extends BaseTest {

    private static final String VALID_TOKEN = "11fae3b4c82533f3b3ddb8152da966d8c66cd6f9bc182f7bd64d3109f55bb22f";

    @Description("401 Unauthorized — request with no Authorization header")
    @Severity(SeverityLevel.CRITICAL)
    @Test
    public void unauthorizedWithoutTokenTest() {
        Response response = RestAssured.given().log().all()
                .baseUri(BASE_URL_GOREST)
                .contentType(ContentType.JSON)
                .get(GOREST_END_POINT)
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 401,
                "Should return 401 when Authorization header is absent");
    }

    @Description("401 Unauthorized — request with an invalid/expired token")
    @Severity(SeverityLevel.CRITICAL)
    @Test
    public void unauthorizedWithInvalidTokenTest() {
        Response response = RestAssured.given().log().all()
                .baseUri(BASE_URL_GOREST)
                .header("Authorization", "Bearer invalid_token_xyz_000")
                .contentType(ContentType.JSON)
                .get(GOREST_END_POINT)
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 401,
                "Should return 401 when token is invalid");
        Assert.assertEquals(response.jsonPath().getString("message"), "Invalid token");
    }

    @Description("404 Not Found — fetch a user ID that does not exist")
    @Severity(SeverityLevel.NORMAL)
    @Test
    public void notFoundForNonExistentUserTest() {
        Response response = RestAssured.given().log().all()
                .baseUri(BASE_URL_GOREST)
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(ContentType.JSON)
                .get(GOREST_END_POINT + "/9999999999")
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 404,
                "Should return 404 for a non-existent user ID");
        Assert.assertEquals(response.jsonPath().getString("message"), "Resource not found");
    }

    @Description("422 Unprocessable Entity — create user with missing required fields (gender, status)")
    @Severity(SeverityLevel.NORMAL)
    @Test
    public void createUserMissingRequiredFieldsTest() {
        String incompleteBody = "{ \"name\": \"TestUser\", \"email\": \"" + StringUtils.getRandomEmailId() + "\" }";

        Response response = RestAssured.given().log().all()
                .baseUri(BASE_URL_GOREST)
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(ContentType.JSON)
                .body(incompleteBody)
                .post(GOREST_END_POINT)
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 422,
                "Should return 422 when required fields are missing");
    }

    @Description("422 Unprocessable Entity — create user with a duplicate email")
    @Severity(SeverityLevel.NORMAL)
    @Test
    public void createUserDuplicateEmailTest() {
        String email = StringUtils.getRandomEmailId();
        User user = User.builder()
                .name("DuplicateEmailTest")
                .email(email)
                .gender("male")
                .status("active")
                .build();

        // First create — should succeed
        RestAssured.given()
                .baseUri(BASE_URL_GOREST)
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(ContentType.JSON)
                .body(user)
                .post(GOREST_END_POINT)
                .then().extract().response();

        // Second create with the same email — should fail with 422
        Response duplicateResponse = RestAssured.given().log().all()
                .baseUri(BASE_URL_GOREST)
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(ContentType.JSON)
                .body(user)
                .post(GOREST_END_POINT)
                .then().log().all().extract().response();

        Assert.assertEquals(duplicateResponse.getStatusCode(), 422,
                "Should return 422 for duplicate email");
        Assert.assertTrue(duplicateResponse.getBody().asString().contains("has already been taken"),
                "Error message should mention 'has already been taken'");
    }
}
