package com.qa.api.gorest.tests;

import com.aventstack.chaintest.plugins.ChainTestListener;
import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import com.qa.api.pojo.User;
import com.qa.api.utils.StringUtils;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeleteUserTest extends BaseTest {

    @BeforeClass
    public void setUpGoRestToken() {
        String token = "11fae3b4c82533f3b3ddb8152da966d8c66cd6f9bc182f7bd64d3109f55bb22f";
        ConfigManager.setProp("bearerToken", token);
    }

    @Test
    public void deleteUserTest() {
        ChainTestListener.log("Delete User Test");

        // Create a user to delete
        User user = User.builder()
                .name(StringUtils.getRandomName())
                .email(StringUtils.getRandomEmailId())
                .gender("male")
                .status("active")
                .build();

        Response postResponse = restClient.postCall(BASE_URL_GOREST, GOREST_END_POINT, user, null, null,
                AuthType.BEARER_TOKEN, ContentType.JSON);
        Integer userId = postResponse.jsonPath().get("id");
        Assert.assertNotNull(userId, "User ID should not be null after creation");

        // Verify the user exists
        Response getResponse = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT + "/" + userId,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertEquals(getResponse.jsonPath().get("id"), userId);

        // Delete the user
        Response deleteResponse = restClient.deleteApiCall(BASE_URL_GOREST, GOREST_END_POINT + "/" + userId,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertEquals(deleteResponse.statusCode(), 204, "Expected 204 No Content on delete");

        // Verify the user is gone
        Response getAfterDelete = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT + "/" + userId,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertEquals(getAfterDelete.statusCode(), 404, "Expected 404 after deletion");
    }
}
