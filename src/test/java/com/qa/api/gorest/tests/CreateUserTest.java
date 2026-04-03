package com.qa.api.gorest.tests;

import com.aventstack.chaintest.plugins.ChainTestListener;
import com.qa.api.base.BaseTest;
import com.qa.api.constants.AppConstants;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import com.qa.api.pojo.User;
import com.qa.api.utils.ExcelUtils;
import com.qa.api.utils.StringUtils;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;

import java.io.File;

public class CreateUserTest extends BaseTest {

    @BeforeClass
    public void setUpGoRestToken() {
        String token = "11fae3b4c82533f3b3ddb8152da966d8c66cd6f9bc182f7bd64d3109f55bb22f";
        ConfigManager.setProp("bearerToken", token);
    }

    @DataProvider
    public Object[][] getUserData() {
        return new Object[][]{
                {"Raghu", "male", "active"},
                {"Ranjith", "male", "inactive"},
                {"priyanka", "female", "active"}
        };
    }

    @Test(dataProvider = "getUserData")
    public void createUserTest(String name, String gender, String status) {
        ChainTestListener.log("Create User Test");
        User user = new User(null, name, StringUtils.getRandomEmailId(), gender, status);

        Response response = restClient.postCall(BASE_URL_GOREST, GOREST_END_POINT, user, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertNotNull(response.jsonPath().get("id"));
        Assert.assertEquals(response.jsonPath().get("name"), name);
        Assert.assertEquals(response.jsonPath().get("gender"), gender);
        Assert.assertEquals(response.jsonPath().get("status"), status);
    }

    @DataProvider
    public Object[][] getUserExcelData(){
      return  ExcelUtils.readDataFromExcel(AppConstants.CREATE_USER_SHEET_NAME);
    }

    @Test(dataProvider = "getUserExcelData")
    public void createUserTestFromExcel(String name, String gender, String status) {
        ChainTestListener.log("Create User Test");
        User user = new User(null, name, StringUtils.getRandomEmailId(), gender, status);

        Response response = restClient.postCall(BASE_URL_GOREST, GOREST_END_POINT, user, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertNotNull(response.jsonPath().get("id"));
        Assert.assertEquals(response.jsonPath().get("name"), name);
        Assert.assertEquals(response.jsonPath().get("gender"), gender);
        Assert.assertEquals(response.jsonPath().get("status"), status);
    }

    @Test(enabled = false)
    public void createUserWithStringTest() {
        ChainTestListener.log("Create User Test using String");
        String requestBody = "{\n" +
                "    \"name\": \"Raghu\",\n" +
                "    \"email\": \"apiAutomatiu3u7940147@open.com\",\n" +
                "    \"gender\": \"male\",\n" +
                "    \"status\": \"active\"\n" +
                "}";


        Response response = restClient.postCall(BASE_URL_GOREST, GOREST_END_POINT, requestBody, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertNotNull(response.jsonPath().get("id"));
        Assert.assertNotNull(response.jsonPath().get("name"));
    }

    @Test(enabled = false)
    public void createUserWithFileTest() {
        ChainTestListener.log("Create User Test using File");
        File userFile = new File("./src/test/resources/jsons/user.json");
        Response response = restClient.postCall(BASE_URL_GOREST, GOREST_END_POINT, userFile, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertNotNull(response.jsonPath().get("id"));
    }
}
