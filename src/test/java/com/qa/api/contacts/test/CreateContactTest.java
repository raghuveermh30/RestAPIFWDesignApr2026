package com.qa.api.contacts.test;

import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import com.qa.api.pojo.Credentials;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class CreateContactTest extends BaseTest {

    private String token;

    @BeforeMethod
    public void getAccessToken() {

        Credentials credentials = Credentials.builder().email("raghuveermh30@gmail.com").password("R@ghumh3017").build();
        Response response = restClient.postCall(BASE_URL_CONTACTS, CONTACTS_USER_LOGIN_ENDPOINT, credentials, null, null, AuthType.NO_AUTH, ContentType.JSON);
        Assert.assertEquals(response.getStatusCode(), 200);
        token = response.jsonPath().getString("token");
        System.out.println("Access Token is : " + token);
        ConfigManager.setProp("bearerToken", token);
    }


    @Test
    public void getAllContacts() {
        Response getResponse = restClient.getApiCall(BASE_URL_CONTACTS, CONTACTS_ALL_ENDPOINT, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertEquals(getResponse.getStatusCode(), 200);
    }
}
