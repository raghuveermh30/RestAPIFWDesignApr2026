package com.qa.api.gorest.tests;

import com.qa.api.base.BaseTest;
import com.qa.api.constants.AppConstants;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import com.qa.api.pojo.User;
import com.qa.api.utils.CSVReaderUtil;
import com.qa.api.utils.StringUtils;
import io.qameta.allure.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * LEARNING: Data-Driven Testing with CSV
 *
 * Data-driven testing = running the same test logic with multiple input sets.
 * CSV is simpler than Excel for version control (plain text, diff-able in Git).
 *
 * Compare the three data sources in this framework:
 *   1. Inline @DataProvider (hardcoded) → CreateUserTest.getUserData()
 *   2. Excel @DataProvider               → CreateUserTest.getUserExcelData()
 *   3. CSV @DataProvider (this class)    → CSVReaderUtil
 *
 * The test data lives in: src/test/resources/testdata/create_users.csv
 * Format: header row (name,gender,status) followed by one test case per row.
 */
@Epic("Data-Driven Testing")
@Story("Create users from CSV test data file")
public class CSVDataDrivenTest extends BaseTest {

    @BeforeClass
    public void setupToken() {
        ConfigManager.setProp("bearerToken", "11fae3b4c82533f3b3ddb8152da966d8c66cd6f9bc182f7bd64d3109f55bb22f");
    }

    @DataProvider(name = "csvUserData")
    public Object[][] getUserDataFromCSV() {
        return CSVReaderUtil.readDataFromCSV(AppConstants.CREATE_USER_CSV_PATH);
    }

    @Description("Create a GoRest user for each row in create_users.csv")
    @Severity(SeverityLevel.NORMAL)
    @Test(dataProvider = "csvUserData")
    public void createUserFromCSVTest(String name, String gender, String status) {
        User user = User.builder()
                .name(name.trim())
                .email(StringUtils.getRandomEmailId())
                .gender(gender.trim())
                .status(status.trim())
                .build();

        Response response = restClient.postCall(BASE_URL_GOREST, GOREST_END_POINT, user,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        Assert.assertNotNull(response.jsonPath().get("id"), "User ID should be returned");
        Assert.assertEquals(response.jsonPath().getString("name"), user.getName());
        Assert.assertEquals(response.jsonPath().getString("gender"), user.getGender());
        Assert.assertEquals(response.jsonPath().getString("status"), user.getStatus());
    }
}
