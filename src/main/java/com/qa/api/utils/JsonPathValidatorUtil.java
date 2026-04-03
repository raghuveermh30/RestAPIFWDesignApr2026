package com.qa.api.utils;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

public class JsonPathValidatorUtil {

    private static String getJsonResponseAsString(Response response) {
        return response.getBody().asString();
    }

    public static <T> T read(Response response, String jsonPath) {
        String jsonResponse = getJsonResponseAsString(response);
        ReadContext context = JsonPath.parse(jsonResponse);
        return context.read(jsonPath);
    }

    public static <T> List<T> readList(Response response, String jsonPath) {
        String jsonResponse = getJsonResponseAsString(response);
        ReadContext context = JsonPath.parse(jsonResponse);
        return context.read(jsonPath);
    }

    public static <T> List<Map<String, T>> readListOfMaps(Response response, String jsonPath) {
        String jsonResponse = getJsonResponseAsString(response);
        ReadContext context = JsonPath.parse(jsonResponse);
        return context.read(jsonPath);
    }


}
