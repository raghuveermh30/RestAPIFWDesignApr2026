package com.qa.api.reqres.tests;

import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import io.qameta.allure.*;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * LEARNING: ReqRes (https://reqres.in) is a hosted REST API for testing.
 * It accepts any payload and simulates realistic HTTP responses without modifying real data.
 * Covers: GET single, GET not found, POST create, PUT update, DELETE, login success/fail, register success/fail.
 */
@Epic("ReqRes Extended Tests")
@Story("Full endpoint coverage — GET, POST, PUT, DELETE, login, register")
public class ReqResExtendedTest extends BaseTest {

    @Description("GET single user by ID — assert nested data fields")
    @Test
    public void getSingleUserTest() {
        Response response = restClient.getApiCall(BASE_URL_REQ_RES, REQRES_ENDPOINT + "/2",
                null, null, AuthType.NO_AUTH, ContentType.JSON);

        Assert.assertEquals(response.getStatusCode(), 200);
        Assert.assertEquals((int) response.jsonPath().get("data.id"), 2);
        Assert.assertNotNull(response.jsonPath().getString("data.email"));
        Assert.assertNotNull(response.jsonPath().getString("data.first_name"));
    }

    @Description("GET single user — 404 for non-existent ID (raw RestAssured — bypasses response spec)")
    @Test
    public void getSingleUserNotFoundTest() {
        Response response = RestAssured.given().log().all()
                .baseUri(BASE_URL_REQ_RES)
                .get(REQRES_ENDPOINT + "/999999")
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 404,
                "Should return 404 for non-existent user");
    }

    @Description("POST create user — assert id and createdAt are returned")
    @Test
    public void createUserTest() {
        String body = "{ \"name\": \"morpheus\", \"job\": \"leader\" }";
        Response response = restClient.postCall(BASE_URL_REQ_RES, REQRES_ENDPOINT, body,
                null, null, AuthType.NO_AUTH, ContentType.JSON);

        Assert.assertEquals(response.getStatusCode(), 201);
        Assert.assertNotNull(response.jsonPath().getString("id"), "id should be returned");
        Assert.assertNotNull(response.jsonPath().getString("createdAt"), "createdAt should be returned");
        Assert.assertEquals(response.jsonPath().getString("name"), "morpheus");
        Assert.assertEquals(response.jsonPath().getString("job"), "leader");
    }

    @Description("PUT update user — assert updatedAt field is returned")
    @Test
    public void updateUserTest() {
        String body = "{ \"name\": \"morpheus\", \"job\": \"zion resident\" }";
        Response response = restClient.putApiCall(BASE_URL_REQ_RES, REQRES_ENDPOINT + "/2", body,
                null, null, AuthType.NO_AUTH, ContentType.JSON);

        Assert.assertEquals(response.getStatusCode(), 200);
        Assert.assertEquals(response.jsonPath().getString("job"), "zion resident");
        Assert.assertNotNull(response.jsonPath().getString("updatedAt"), "updatedAt should be returned");
    }

    @Description("DELETE user — 204 No Content (raw RestAssured)")
    @Test
    public void deleteUserTest() {
        Response response = RestAssured.given().log().all()
                .baseUri(BASE_URL_REQ_RES)
                .delete(REQRES_ENDPOINT + "/2")
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 204,
                "DELETE should return 204 No Content");
        Assert.assertTrue(response.getBody().asString().isEmpty(),
                "Response body should be empty on 204");
    }

    @Description("POST /api/login — successful login returns a token")
    @Test
    public void loginSuccessTest() {
        String body = "{ \"email\": \"eve.holt@reqres.in\", \"password\": \"cityslicka\" }";
        Response response = restClient.postCall(BASE_URL_REQ_RES, "/api/login", body,
                null, null, AuthType.NO_AUTH, ContentType.JSON);

        Assert.assertEquals(response.getStatusCode(), 200);
        Assert.assertNotNull(response.jsonPath().getString("token"),
                "Login should return a non-null token");
    }

    @Description("POST /api/login — missing password returns 400 with error message")
    @Test
    public void loginMissingPasswordTest() {
        String body = "{ \"email\": \"peter@klaven.com\" }";
        Response response = RestAssured.given().log().all()
                .baseUri(BASE_URL_REQ_RES)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/login")
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 400);
        Assert.assertEquals(response.jsonPath().getString("error"), "Missing password");
    }

    @Description("POST /api/register — successful registration returns id and token")
    @Test
    public void registerSuccessTest() {
        String body = "{ \"email\": \"eve.holt@reqres.in\", \"password\": \"pistol\" }";
        Response response = restClient.postCall(BASE_URL_REQ_RES, "/api/register", body,
                null, null, AuthType.NO_AUTH, ContentType.JSON);

        Assert.assertEquals(response.getStatusCode(), 200);
        Assert.assertNotNull(response.jsonPath().getString("token"));
        Assert.assertNotNull(response.jsonPath().get("id"));
    }

    @Description("POST /api/register — missing password returns 400 with error message")
    @Test
    public void registerMissingPasswordTest() {
        String body = "{ \"email\": \"sydney@fife\" }";
        Response response = RestAssured.given().log().all()
                .baseUri(BASE_URL_REQ_RES)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/register")
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 400);
        Assert.assertEquals(response.jsonPath().getString("error"), "Missing password");
    }
}
