package com.qa.api.basicauth;

import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BasicAuth extends BaseTest {

    @Test
    public void basicAuthTest() {
        Response response = restClient.getApiCall(BASE_URL_HERO_BASIC, "basic_auth", null, null, AuthType.BASIC_AUTH, ContentType.ANY);
        response.prettyPrint();
        Assert.assertEquals(response.getStatusCode(), 200);
    }


}

