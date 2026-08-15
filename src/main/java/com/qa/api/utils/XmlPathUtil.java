package com.qa.api.utils;

import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;

import java.util.List;

public class XmlPathUtil {

    /*
     * Read a single value from an XML response using GPath expression.
     *
     * @param response  REST Assured Response object
     * @param gPath     GPath expression, e.g. "circuits.circuit[0].name"
     * @return value at that path
     */
    public static <T> T read(Response response, String gPath) {
        return response.xmlPath().get(gPath);
    }

    /*
     * Read a list of values from an XML response using GPath.
     *
     * @param response REST Assured Response object
     * @param gPath    GPath expression, e.g. "circuits.circuit.name"
     * @return list of matching values
     */
    public static List<String> readList(Response response, String gPath) {
        return response.xmlPath().getList(gPath);
    }

    /*
     * Return the raw XmlPath object for advanced navigation.
     */
    public static XmlPath getXmlPath(Response response) {
        return response.xmlPath();
    }
}

