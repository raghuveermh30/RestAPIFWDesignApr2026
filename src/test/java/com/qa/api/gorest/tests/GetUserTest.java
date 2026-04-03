package com.qa.api.gorest.tests;

import com.aventstack.chaintest.plugins.ChainTestListener;
import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import io.qameta.allure.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

@Epic("Go Rest Get User Test ")
@Story("SUP-12345 : Get User Test for Go Rest API")
@Owner("Raghuveer")
@Severity(SeverityLevel.CRITICAL)
public class GetUserTest extends BaseTest {

    @Description("Setup Go Rest Auth Token")
    @BeforeClass
    public void setUpGoRestToken() {
        String token = "11fae3b4c82533f3b3ddb8152da966d8c66cd6f9bc182f7bd64d3109f55bb22f";
        ConfigManager.setProp("bearerToken", token);
    }

    @Description("Get All User Test")
    @Test
    public void getAllUsersTest() {
        ChainTestListener.log("Get All Users Api Test");
        Response response = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertTrue(response.statusLine().contains("OK"));
    }

    @Description("Get All User Test with Query Param")
    @Test
    public void getAllUsersWithQueryParamTest() {
        ChainTestListener.log("Get All Users Api Test with Query Params");
        Map<String, String> queryParam = new HashMap<>();
        queryParam.put("name", "raghu");
        queryParam.put("status", "active");

        Response response = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT, queryParam, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertTrue(response.statusLine().contains("OK"));
    }

    @Description("Get Single User Test")
    @Test
    public void getSingleUserTest() {
        String userId = "8417366";
        Response response = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT + "/" + userId, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertTrue(response.statusLine().contains("OK"));
        Assert.assertEquals(response.jsonPath().get("id").toString(), userId);
    }


}
