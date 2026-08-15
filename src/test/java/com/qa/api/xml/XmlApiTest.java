package com.qa.api.xml;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.qa.api.utils.XmlPathUtil;
import io.restassured.RestAssured;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * LEARNING: XML Response Parsing with Rest Assured XmlPath (GPath)
 *
 * Not all APIs return JSON — XML is common in legacy enterprise systems, SOAP services,
 * and transport APIs. Rest Assured handles XML natively via xmlPath() using Groovy GPath.
 *
 * GPath syntax: "rootElement.childElement.attribute"
 * Attribute access: prefix "@" — e.g. "circuits.circuit.@id"
 * List extraction: getList("circuits.circuit.name") → List<String>
 *
 * We use WireMock to serve a mock XML response so this test works offline.
 */
public class XmlApiTest {

    private static final int MOCK_PORT = 8091;
    private WireMockServer wireMockServer;

    private static final String CIRCUITS_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<circuits>" +
            "  <circuit id=\"albert_park\">" +
            "    <name>Albert Park Grand Prix Circuit</name>" +
            "    <country>Australia</country>" +
            "    <city>Melbourne</city>" +
            "    <lat>-37.8497</lat>" +
            "    <lon>144.968</lon>" +
            "  </circuit>" +
            "  <circuit id=\"bahrain\">" +
            "    <name>Bahrain International Circuit</name>" +
            "    <country>Bahrain</country>" +
            "    <city>Sakhir</city>" +
            "    <lat>26.0325</lat>" +
            "    <lon>50.5106</lon>" +
            "  </circuit>" +
            "  <circuit id=\"shanghai\">" +
            "    <name>Shanghai International Circuit</name>" +
            "    <country>China</country>" +
            "    <city>Shanghai</city>" +
            "    <lat>31.3389</lat>" +
            "    <lon>121.220</lon>" +
            "  </circuit>" +
            "</circuits>";

    @BeforeClass
    public void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().port(MOCK_PORT));
        wireMockServer.start();
        WireMock.configureFor("localhost", MOCK_PORT);

        stubFor(get(urlEqualTo("/api/f1/circuits.xml"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(CIRCUITS_XML)));
    }

    @AfterClass
    public void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    public void parseXmlAndGetAllCircuitNamesTest() {
        Response response = RestAssured.given().log().all()
                .baseUri("http://localhost:" + MOCK_PORT)
                .get("/api/f1/circuits.xml")
                .then().log().all().extract().response();

        Assert.assertEquals(response.getStatusCode(), 200);
        Assert.assertTrue(response.contentType().contains("xml"));

        // XmlPathUtil wrapper
        List<String> names = XmlPathUtil.readList(response, "circuits.circuit.name");
        System.out.println("Circuit names: " + names);

        Assert.assertEquals(names.size(), 3, "Should have 3 circuits");
        Assert.assertTrue(names.contains("Albert Park Grand Prix Circuit"));
        Assert.assertTrue(names.contains("Bahrain International Circuit"));
        Assert.assertTrue(names.contains("Shanghai International Circuit"));
    }

    @Test
    public void parseXmlAndGetAllCountriesTest() {
        Response response = RestAssured.given()
                .baseUri("http://localhost:" + MOCK_PORT)
                .get("/api/f1/circuits.xml")
                .then().extract().response();

        List<String> countries = XmlPathUtil.readList(response, "circuits.circuit.country");
        System.out.println("Countries: " + countries);

        Assert.assertTrue(countries.contains("Australia"));
        Assert.assertTrue(countries.contains("Bahrain"));
        Assert.assertTrue(countries.contains("China"));
    }

    @Test
    public void parseXmlGetSingleNodeByIndexTest() {
        Response response = RestAssured.given()
                .baseUri("http://localhost:" + MOCK_PORT)
                .get("/api/f1/circuits.xml")
                .then().extract().response();

        // GPath indexing: circuit[0] = first circuit
        String firstCircuitName = XmlPathUtil.read(response, "circuits.circuit[0].name");
        String firstCircuitCity = XmlPathUtil.read(response, "circuits.circuit[0].city");

        System.out.println("First circuit: " + firstCircuitName + " / " + firstCircuitCity);

        Assert.assertEquals(firstCircuitName, "Albert Park Grand Prix Circuit");
        Assert.assertEquals(firstCircuitCity, "Melbourne");
    }

    @Test
    public void parseXmlGetAttributeValueTest() {
        Response response = RestAssured.given()
                .baseUri("http://localhost:" + MOCK_PORT)
                .get("/api/f1/circuits.xml")
                .then().extract().response();

        // Attribute access with '@' prefix
        List<String> ids = XmlPathUtil.readList(response, "circuits.circuit.@id");
        System.out.println("Circuit IDs (attributes): " + ids);

        Assert.assertEquals(ids.size(), 3);
        Assert.assertTrue(ids.contains("albert_park"));
        Assert.assertTrue(ids.contains("bahrain"));
        Assert.assertTrue(ids.contains("shanghai"));
    }

    @Test
    public void parseXmlRawXmlPathObjectTest() {
        Response response = RestAssured.given()
                .baseUri("http://localhost:" + MOCK_PORT)
                .get("/api/f1/circuits.xml")
                .then().extract().response();

        // Using the raw XmlPath object for advanced GPath queries
        XmlPath xmlPath = XmlPathUtil.getXmlPath(response);

        String secondCircuitCountry = xmlPath.getString("circuits.circuit[1].country");
        System.out.println("Second circuit country: " + secondCircuitCountry);
        Assert.assertEquals(secondCircuitCountry, "Bahrain");

        int totalCircuits = xmlPath.getList("circuits.circuit").size();
        System.out.println("Total circuits: " + totalCircuits);
        Assert.assertEquals(totalCircuits, 3);
    }
}
