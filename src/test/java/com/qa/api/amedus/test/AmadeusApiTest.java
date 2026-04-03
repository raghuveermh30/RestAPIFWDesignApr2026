package com.qa.api.amedus.test;

import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import org.apache.groovy.util.Maps;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Map;

public class AmadeusApiTest extends BaseTest {

    private String accessToken;

    @BeforeTest
    public void generateOauth2ApiToken() {
        Response postApiResponse = restClient.postApiCall(BASE_URL_OAUTH2_AMADEUS, AMADEUS_OAUTH2_TOKEN_ENDPOINT, ConfigManager.getProp(""), ConfigManager.getProp(""),
                ConfigManager.getProp(""), ContentType.URLENC);
        postApiResponse.prettyPrint();
        accessToken = postApiResponse.jsonPath().get("access_token");
        System.out.println("Access Token is + " + accessToken);
        ConfigManager.setProp("bearerToken", accessToken);

    }

    @Test
    public void getFightBookingDetails() {
        Map<String, String> queryParams = Maps.of("origin", "PAR", "maxPrice", "200");
        Response getApiResponse = restClient.getApiCall(BASE_URL_OAUTH2_AMADEUS, AMADEUS_GET_ENDPOINT, queryParams, null, AuthType.BEARER_TOKEN, ContentType.ANY);
        getApiResponse.prettyPrint();
        Assert.assertEquals(getApiResponse.getStatusCode(), 200);
    }


}
