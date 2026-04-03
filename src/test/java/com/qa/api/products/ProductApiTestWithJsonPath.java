package com.qa.api.products;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import com.qa.api.utils.JsonPathValidatorUtil;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class ProductApiTestWithJsonPath extends BaseTest {

    @Test
    public void getAllProductDetails() {
        Response getResponse = restClient.getApiCall(BASE_URL_PRODUCT, PRODUCTS_ENDPOINT, null, null, AuthType.NO_AUTH, ContentType.JSON);
        Assert.assertEquals(getResponse.getStatusCode(), 200);

        List<Number> prices = JsonPathValidatorUtil.readList(getResponse, "$[?(@.price > 50)].price");
        prices.forEach(System.out::println);

        System.out.println("****************");

        List<Number> ids = JsonPathValidatorUtil.readList(getResponse, "$[?(@.price > 50)].id");
        ids.forEach(System.out::println);
        System.out.println("****************");

        List<Double> rateList = JsonPathValidatorUtil.readList(getResponse, "$[?(@.price > 50)].rating.rate");
        rateList.forEach(System.out::println);
        System.out.println("****************");

        List<Integer> countList = JsonPathValidatorUtil.readList(getResponse, "$[?(@.price > 50)].rating.count");
        countList.forEach(System.out::println);
        System.out.println("****************");

        //Get List of MAP
        List<Map<String, Object>> idTitleList = JsonPathValidatorUtil.readListOfMaps(getResponse, "$.[*].['id','title']");
        idTitleList.forEach(System.out::println);
        System.out.println("****************");

        List<Map<String, Object>> idTitleCatList = JsonPathValidatorUtil.readListOfMaps(getResponse, "$.[*].['id','title', 'category']");
        idTitleCatList.forEach(System.out::println);
        System.out.println("****************");

        List<Map<String, Object>> jewelleryList = JsonPathValidatorUtil.readList(getResponse, "$[?(@.category == 'jewelery')]..['title','price']");
        System.out.println(jewelleryList.size());

        for (Map<String, Object> product : jewelleryList) {
            String title = product.get("title").toString();
            Number price = (Number) product.get("price");
            System.out.println("title:" + title);
            System.out.println("price:" + price);
            System.out.println("***************");
        }

        //Fetch Single Attributes
        //Get Minimum Price
        Double minPrice = JsonPathValidatorUtil.read(getResponse, "min($[*].price)");
        System.out.println("Minimum Price is " + minPrice);

        //Get Maximum Price
        Double maxPrice = JsonPathValidatorUtil.read(getResponse, "max($[*].price)");
        System.out.println("Maximum Price is " + maxPrice);

        //Get Average Price
        Double averagePrice = JsonPathValidatorUtil.read(getResponse, "avg($[*].price)");
        System.out.println("Average Price is " + averagePrice);

        //Get stddev() - Provides the standard deviation value of an array of numbers
        Double stddev = JsonPathValidatorUtil.read(getResponse, "stddev($[*].price)");
        System.out.println("Average Price is " + stddev);

        // length() - Provides the length of an array
        Integer arrayLength = JsonPathValidatorUtil.read(getResponse, "length($)");
        System.out.println("Array length: " + arrayLength);
        System.out.println("-----------");
    }
}
