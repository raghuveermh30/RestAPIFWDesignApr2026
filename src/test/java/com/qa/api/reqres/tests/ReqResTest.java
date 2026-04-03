package com.qa.api.reqres.tests;

import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
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
}
