package com.qa.api.reqres.tests;

import com.aventstack.chaintest.plugins.ChainTestListener;
import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

public class ReqResTest extends BaseTest {

    @Test
    public void getUserTest() {

        Map<String, String> stringIntegerMap = new HashMap<>();
        stringIntegerMap.put("page", "2");

        Response response = restClient.getApiCall(BASE_URL_REQ_RES, REQRES_ENDPOINT, stringIntegerMap, null, AuthType.NO_AUTH, ContentType.JSON);
        Assert.assertEquals(response.getStatusCode(), 200);
    }

    @Test
    public void getSingleUserNotFoundTest() {
        ChainTestListener.log("Get Single User Not Found Test — expects 404 for non-existent user ID 999");

        Response response = RestAssured.given().log().all()
                .baseUri(BASE_URL_REQ_RES)
                .contentType(ContentType.JSON)
                .get(REQRES_ENDPOINT + "/999")
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 404);
        Assert.assertEquals(response.getBody().asString().trim(), "{}");
    }
}
