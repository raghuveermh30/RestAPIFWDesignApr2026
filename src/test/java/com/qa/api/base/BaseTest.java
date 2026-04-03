package com.qa.api.base;

import com.aventstack.chaintest.plugins.ChainTestListener;
import com.qa.api.client.RestClient;
import com.qa.api.manager.ConfigManager;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Listeners;

@Listeners(ChainTestListener.class)
public class BaseTest {

    protected RestClient restClient;

    //***** API Base URLS ****
    protected static String BASE_URL_GOREST = null;


    protected final static String BASE_URL_CONTACTS = "https://thinking-tester-contact-list.herokuapp.com";
    protected final static String BASE_URL_REQ_RES = "https://reqres.in";
    protected final static String BASE_URL_HERO_BASIC = "https://the-internet.herokuapp.com";
    protected final static String BASE_URL_PRODUCT = "https://fakestoreapi.com";
    protected final static String BASE_URL_OAUTH2_AMADEUS = "https://test.api.amadeus.com";
    protected final static String BASE_URL_ERGAST_CIRCUIT = "http://ergast.com";


    //***** API END Points ****
    protected static final String GOREST_END_POINT = "/public/v2/users";
    protected static final String CONTACTS_ALL_ENDPOINT = "/contacts";
    protected static final String CONTACTS_USER_LOGIN_ENDPOINT = "/users/login";
    protected static final String AMADEUS_OAUTH2_TOKEN_ENDPOINT = "/v1/security/oauth2/token";
    protected static final String AMADEUS_GET_ENDPOINT = "/v1/shopping/flight-destinations";
    protected final static String ERGAST_CIRCUIT_ENDPOINT = "/api/f1/2017/circuits.xml";
    protected final static String PRODUCTS_ENDPOINT = "/products";
    protected static final String REQRES_ENDPOINT = "/api/users";

    @BeforeSuite
    public void initialSetup() {
        RestAssured.filters(new AllureRestAssured());
        BASE_URL_GOREST = ConfigManager.getProp("baseurl.gorest").trim();
    }

    @BeforeTest
    public void setup() {
        restClient = new RestClient();
    }


}
