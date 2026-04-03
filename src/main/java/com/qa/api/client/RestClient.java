package com.qa.api.client;

import com.aventstack.chaintest.plugins.ChainTestListener;
import com.qa.api.constants.AuthType;
import com.qa.api.exception.APIException;
import com.qa.api.manager.ConfigManager;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import java.io.File;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static io.restassured.RestAssured.expect;

public class RestClient {

    //Define Response Spec
    private static final ResponseSpecification responseSpec200 = expect().statusCode(200);
    private static final ResponseSpecification responseSpec201 = expect().statusCode(201);
    private static final ResponseSpecification responseSpec204 = expect().statusCode(204);
    private static final ResponseSpecification responseSpec400 = expect().statusCode(400);
    private static final ResponseSpecification responseSpec200or201 = expect().statusCode(anyOf(equalTo(200), equalTo(201)));
    private static final ResponseSpecification responseSpec200or404 = expect().statusCode(anyOf(equalTo(200), equalTo(404)));
    private static final ResponseSpecification responseSpec204or404 = expect().statusCode(anyOf(equalTo(204), equalTo(404)));


    private RequestSpecification setupRequest(String baseUrl, AuthType authType, ContentType contentType) {
        ChainTestListener.log("Base Url is : "+baseUrl);
        RequestSpecification requestSpecification = RestAssured.given().log().all().
                baseUri(baseUrl).
                contentType(contentType).
                accept(contentType);

        switch (authType) {
            case BEARER_TOKEN:
                requestSpecification.header("Authorization", "Bearer " + ConfigManager.getProp("bearerToken"));
                break;
            case BASIC_AUTH:
                requestSpecification.header("Authorization", "Basic " + generateBasicAuth());
                break;
            case API_KEY:
                requestSpecification.header("x-api-key", "Api key");
                break;
            case NO_AUTH:
                System.out.println("Auth is not required");
                break;
            default:
                System.out.println("this auth is not supported, please find the correct auth type");
                throw new APIException("=== Invalid AuthType ===");
        }
        return requestSpecification;
    }

    private String generateBasicAuth() {
        String credentials = ConfigManager.getProp("basicUserName") + ":" + ConfigManager.getProp("basicPassword");
        return Base64.getEncoder().encodeToString(credentials.getBytes());

    }

    @Step("Calling Apply Parameters with requestSpecification : {0} ,  QueryParam {1}, PathParam {2}")
    private void applyParams(RequestSpecification requestSpecification, Map<String, String> queryParam, Map<String, String> pathParam) {
        ChainTestListener.log("Query Params : "+queryParam);
        ChainTestListener.log("Path Params : "+pathParam);
        if (queryParam != null) {
            requestSpecification.queryParams(queryParam);
        }

        if (pathParam != null) {
            requestSpecification.pathParams(pathParam);
        }
    }

    //CRUD Operations

    /*
     * This method is used to call the GET APIs
     *
     * @param baseUrl
     * @param endPoint
     * @param queryParams
     * @param pathParams
     * @param authType
     * @param contentType
     * @return It returns the Get API call Response
     */
    @Step("Calling Get Api with base url : {0} , endpoint {1}, QueryParam {2}, PathParam {3}, AuthType {4} and Content Type {5}")
    public Response getApiCall(String baseUrl, String endPoint,
                               Map<String, String> queryParams, Map<String, String> pathParams,
                               AuthType authType, ContentType contentType) {

        RequestSpecification request = setupRequest(baseUrl, authType, contentType);
        applyParams(request, queryParams, pathParams);

        Response response = request.get(endPoint).then().spec(responseSpec200or404).log().all().extract().response();
        response.prettyPrint();
        return response;
    }

    /*
     * This method is used to call the Post APIs
     * @param baseUrl
     * @param endpoint
     * @param body
     * @param queryParams
     * @param pathParams
     * @param authType
     * @param contentType
     * @return It returns the Post API call Response
     */
    @Step("Calling Post Api with base url : {0} , endpoint {1}, body {2}, QueryParam {3}, PathParam {4}, AuthType {5} and Content Type {6}")
    public <T> Response postCall(String baseUrl, String endpoint, T body,
                                 Map<String, String> queryParams, Map<String, String> pathParams,
                                 AuthType authType, ContentType contentType) {

        RequestSpecification request = setupRequest(baseUrl, authType, contentType);
        applyParams(request, queryParams, pathParams);

        Response response = request.body(body).log().all().post(endpoint).then().log().all().spec(responseSpec200or201).extract().response();
        response.prettyPrint();
        return response;
    }

    /*
     * This method is used to call the Post APIs for File Input Type
     * @param baseUrl
     * @param endpoint
     * @param body
     * @param queryParams
     * @param pathParams
     * @param authType
     * @param contentType
     * @return It returns the Post API call Response
     */
    @Step("Calling Post Api with base url : {0} , endpoint {1}, File body {2}, QueryParam {3}, PathParam {4}, AuthType {5} and Content Type {6}")
    public <T> Response postCall(String baseUrl, String endpoint, File file,
                                 Map<String, String> queryParams, Map<String, String> pathParams,
                                 AuthType authType, ContentType contentType) {

        RequestSpecification request = setupRequest(baseUrl, authType, contentType);
        applyParams(request, queryParams, pathParams);

        Response response = request.log().all().body(file).post(endpoint).then().log().all().spec(responseSpec200or201).extract().response();
        response.prettyPrint();
        return response;
    }

    /*
     * This method is used to call the POST APIs for OAuth 2.0
     * @param baseUrl
     * @param endPoint
     * @param queryMap
     * @param pathMap
     * @param authType
     * @param contentType
     * @return it returns the POST APIs for OAuth 2.0
     */
    @Step("Calling Post Api with base url : {0} , endpoint {1}, clientId {2}, clientSecret {3}, grantType {4}, and Content Type {5}")
    public Response postApiCall(String baseUrl, String endPoint, String clientId, String clientSecret, String grantType,
                                ContentType contentType) {
        Response response = RestAssured.given().log().all()
                .contentType(contentType)
                .formParam("grant_type", grantType)
                .formParam("client_id", clientId)
                .formParam("client_secret", clientSecret)
                .post(baseUrl + endPoint).then().log().all().extract().response();
        response.prettyPrint();
        return response;
    }

    /*
     *
     * @param baseUrl
     * @param endpoint
     * @param body
     * @param queryParams
     * @param pathParams
     * @param authType
     * @param contentType
     * @return
     * @param <T>
     */
    @Step("Calling Put Api with base url : {0} , endpoint {1}, File body {2}, QueryParam {3}, PathParam {4}, AuthType {5} and Content Type {6}")
    public <T> Response putApiCall(String baseUrl, String endpoint, T body,
                                   Map<String, String> queryParams, Map<String, String> pathParams,
                                   AuthType authType, ContentType contentType) {

        RequestSpecification request = setupRequest(baseUrl, authType, contentType);
        applyParams(request, queryParams, pathParams);

        Response response = request.log().all().body(body).put(endpoint).then().log().all().spec(responseSpec200).extract().response();
        response.prettyPrint();
        return response;
    }

    /*
     *
     *
     * @param baseUrl
     * @param endpoint
     * @param body
     * @param queryParams
     * @param pathParams
     * @param authType
     * @param contentType
     * @return
     * @param <T>
     */
    @Step("Calling Patch Api with base url : {0} , endpoint {1}, File body {2}, QueryParam {3}, PathParam {4}, AuthType {5} and Content Type {6}")
    public <T> Response patchApiCall(String baseUrl, String endpoint, T body,
                                     Map<String, String> queryParams, Map<String, String> pathParams,
                                     AuthType authType, ContentType contentType) {

        RequestSpecification request = setupRequest(baseUrl, authType, contentType);
        applyParams(request, queryParams, pathParams);

        Response response = request.log().all().body(body).patch(endpoint).then().log().all().spec(responseSpec200).extract().response();
        response.prettyPrint();
        return response;
    }

    /*
     *
     * @param baseUrl
     * @param endpoint
     * @param queryParams
     * @param pathParams
     * @param authType
     * @param contentType
     * @return
     */
    @Step("Calling Delete Api with base url : {0} , endpoint {1}, File body {2}, QueryParam {3}, PathParam {4}, AuthType {5} and Content Type {6}")
    public Response deleteApiCall(String baseUrl, String endpoint,
                                  Map<String, String> queryParams, Map<String, String> pathParams,
                                  AuthType authType, ContentType contentType) {

        RequestSpecification request = setupRequest(baseUrl, authType, contentType);
        applyParams(request, queryParams, pathParams);

        Response response = request.log().all().delete(endpoint).then().log().all().spec(responseSpec204or404).extract().response();
        response.prettyPrint();
        return response;
    }
}
