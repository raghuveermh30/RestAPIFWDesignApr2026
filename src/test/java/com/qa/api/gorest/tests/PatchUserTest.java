package com.qa.api.gorest.tests;

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
import org.testng.annotations.Test;

/**
 * LEARNING: PATCH vs PUT
 * PUT  = full replace — you must send ALL fields; missing fields are cleared/nulled.
 * PATCH = partial update — you send ONLY the fields you want to change; others stay as-is.
 */
@Epic("GoRest PATCH User")
@Story("Demonstrates HTTP PATCH for partial resource updates")
public class PatchUserTest extends BaseTest {

    @BeforeClass
    public void setupToken() {
        ConfigManager.setProp("bearerToken", "11fae3b4c82533f3b3ddb8152da966d8c66cd6f9bc182f7bd64d3109f55bb22f");
    }

    @Description("PATCH only the status field — name and email must stay unchanged")
    @Severity(SeverityLevel.NORMAL)
    @Test
    public void patchUserStatusTest() {
        // Step 1: Create a user
        User user = User.builder()
                .name(StringUtils.getRandomName())
                .email(StringUtils.getRandomEmailId())
                .gender("male")
                .status("active")
                .build();

        Response postResponse = restClient.postCall(BASE_URL_GOREST, GOREST_END_POINT, user,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Integer userId = postResponse.jsonPath().get("id");
        Assert.assertNotNull(userId, "User ID should not be null after creation");

        // Step 2: PATCH — send ONLY the status field
        User patch = User.builder().status("inactive").build();
        Response patchResponse = restClient.patchApiCall(BASE_URL_GOREST, GOREST_END_POINT + "/" + userId,
                patch, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        Assert.assertEquals(patchResponse.getStatusCode(), 200);
        Assert.assertEquals(patchResponse.jsonPath().getString("status"), "inactive",
                "Status should be updated to inactive");
        Assert.assertEquals(patchResponse.jsonPath().getString("name"), user.getName(),
                "Name must be unchanged after PATCH");
        Assert.assertEquals(patchResponse.jsonPath().getString("email"), user.getEmail(),
                "Email must be unchanged after PATCH");
    }

    @Description("PATCH only the name field — status must stay unchanged")
    @Severity(SeverityLevel.NORMAL)
    @Test
    public void patchUserNameTest() {
        User user = User.builder()
                .name(StringUtils.getRandomName())
                .email(StringUtils.getRandomEmailId())
                .gender("female")
                .status("active")
                .build();

        Response postResponse = restClient.postCall(BASE_URL_GOREST, GOREST_END_POINT, user,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Integer userId = postResponse.jsonPath().get("id");
        Assert.assertNotNull(userId);

        User patch = User.builder().name("PatchedName_" + System.currentTimeMillis()).build();
        Response patchResponse = restClient.patchApiCall(BASE_URL_GOREST, GOREST_END_POINT + "/" + userId,
                patch, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        Assert.assertEquals(patchResponse.getStatusCode(), 200);
        Assert.assertEquals(patchResponse.jsonPath().getString("name"), patch.getName(),
                "Name should be updated");
        Assert.assertEquals(patchResponse.jsonPath().getString("status"), "active",
                "Status must remain active after name-only PATCH");
    }
}
