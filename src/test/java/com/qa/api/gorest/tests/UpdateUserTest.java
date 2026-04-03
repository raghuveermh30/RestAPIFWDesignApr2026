package com.qa.api.gorest.tests;

import com.aventstack.chaintest.plugins.ChainTestListener;
import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import com.qa.api.pojo.User;
import com.qa.api.utils.StringUtils;
import io.qameta.allure.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@Epic("Go Rest Update User Test ")
@Story("SUP-12345 : Update User Test for Go Rest API")
public class UpdateUserTest extends BaseTest {

    @BeforeClass
    public void setUpGoRestToken() {
        String token = "11fae3b4c82533f3b3ddb8152da966d8c66cd6f9bc182f7bd64d3109f55bb22f";
        ConfigManager.setProp("bearerToken", token);
    }

    // AAA Pattern

    // Create the User --> Get the User --> Update the User

    @Description("Updating the user id")
    @Owner("Raghu")
    @Severity(SeverityLevel.CRITICAL)
    @Test
    public void updateUserTest() {
        ChainTestListener.log("Update User Test");
        User user = User.builder().name(StringUtils.getRandomName()).email(StringUtils.getRandomEmailId()).status("active").gender("male").build();

        Response postResponse = restClient.postCall(BASE_URL_GOREST, GOREST_END_POINT, user, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        Integer userId = postResponse.jsonPath().get("id");
        System.out.println("User Id is : " + userId);
        Assert.assertNotNull(postResponse.jsonPath().get("id"));
        Assert.assertNotNull(postResponse.jsonPath().get("name"));

        //Get the User Details
        Response getResponse = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT + "/" + userId, null, null
                , AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertEquals(getResponse.jsonPath().get("id"), userId);
        Assert.assertNotNull(getResponse.jsonPath().get("name"));

        //Update the User id using the same User Id
        user.setName("Raghuveer Automation");
        user.setStatus("inactive");

        Response updateResponse = restClient.putApiCall(BASE_URL_GOREST, GOREST_END_POINT + "/" + userId, user, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertEquals(updateResponse.jsonPath().get("name"), "Raghuveer Automation");
        Assert.assertEquals(updateResponse.jsonPath().get("status"), "inactive");

        //Get the User Details
        Response getResponse1 = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT + "/" + userId, null, null
                , AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertEquals(getResponse1.jsonPath().get("id"), userId);
        Assert.assertEquals(getResponse1.jsonPath().get("name"), "Raghuveer Automation");
        Assert.assertEquals(getResponse1.jsonPath().get("status"), "inactive");

    }
}
