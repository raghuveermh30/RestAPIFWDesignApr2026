package com.qa.api.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;

public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static <T> T deserialize(Response response, Class<T> targetClass) {

        try {
            return objectMapper.readValue(response.getBody().asString(), targetClass);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("deserialize is failed..."+targetClass.getName());
        }

    }


}
