package com.qa.api.products;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import com.qa.api.pojo.Products;
import com.qa.api.utils.JsonUtil;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;

public class ProductApiTest extends BaseTest {

    @Test
    public void getAllProductDetails() throws JsonProcessingException {
        Response response = restClient.getApiCall(BASE_URL_PRODUCT, PRODUCTS_ENDPOINT, null, null, AuthType.NO_AUTH, ContentType.JSON);
        Assert.assertEquals(response.getStatusCode(), 200);

        Products[] products = JsonUtil.deserialize(response, Products[].class);

        System.out.println(Arrays.toString(products));

        for (Products product : products) {
            System.out.println("id : " + product.getId());
            System.out.println("title : " + product.getTitle());
            System.out.println("price : " + product.getPrice());
            System.out.println("description : " + product.getDescription());
            System.out.println("category : " + product.getCategory());
            System.out.println("rate : " + product.getRating().getRate());
            System.out.println("count : " + product.getRating().getCount());
        }
    }

}
