package com.qa.api.gorest.tests;

import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import com.qa.api.pojo.User;
import com.qa.api.utils.JsonUtil;
import com.qa.api.utils.StringUtils;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


@Epic("Go Rest Update User Test with Deserialzation")
@Story("SUP-12345 : Update User Test for Go Rest API")
public class GetSingleUserWithSerializationTest extends BaseTest {

    @BeforeClass
    public void setUpGoRestToken() {
        String token = "11fae3b4c82533f3b3ddb8152da966d8c66cd6f9bc182f7bd64d3109f55bb22f";
        ConfigManager.setProp("bearerToken", token);
    }

    @Test
    public void updateUserTest() {

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

        User userPojo = JsonUtil.deserialize(getResponse, User.class);

        Assert.assertEquals(userPojo.getName(), user.getName());
        Assert.assertEquals(userPojo.getEmail(), user.getEmail());


    }
}
