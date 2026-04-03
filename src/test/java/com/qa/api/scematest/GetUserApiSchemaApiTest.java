package com.qa.api.scematest;

import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import com.qa.api.pojo.User;
import com.qa.api.schema.SchemaValidator;
import com.qa.api.utils.StringUtils;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GetUserApiSchemaApiTest extends BaseTest {

    private String token;

    @BeforeClass
    public void setupToken() {
         token = "11fae3b4c82533f3b3ddb8152da966d8c66cd6f9bc182f7bd64d3109f55bb22f";
        ConfigManager.setProp("bearerToken", token);
    }

    @Test
    public void getUsersAPISchemaTest() {
        Response response = restClient.getApiCall(BASE_URL_GOREST, GOREST_END_POINT, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertTrue(SchemaValidator.validateSchema(response, "schema/get-user-schema.json"));
    }

    @Test
    public void createUserAPISchemaTest() {
        User user = User.builder()
                .name("api")
                .status("active")
                .email(StringUtils.getRandomEmailId())
                .gender("female")
                .build();

        Response response = restClient.postCall(BASE_URL_GOREST, GOREST_END_POINT, user,null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertTrue(SchemaValidator.validateSchema(response, "schema/create-user-schema.json"));
    }


}
