package com.qa.api.contacts.test;

import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import com.qa.api.pojo.Contact;
import com.qa.api.pojo.Credentials;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * LEARNING: Contacts API full CRUD
 *
 * API: https://thinking-tester-contact-list.herokuapp.com
 * Auth: Bearer token obtained from POST /users/login
 *
 * Demonstrates:
 *   - Token-based auth flow (login first, store token, reuse in all tests)
 *   - POST /contacts  → create contact (201)
 *   - GET /contacts/{id} → fetch single contact (200)
 *   - PUT /contacts/{id} → full update contact (200)
 *   - DELETE /contacts/{id} → this API returns 200 (not 204), so we use raw RestAssured
 *     instead of RestClient.deleteApiCall which expects 204/404.
 *     This teaches when to break out of a wrapper and call the library directly.
 */
public class GetContactsTest extends BaseTest {

    @BeforeClass
    public void authenticate() {
        Credentials credentials = Credentials.builder()
                .email("raghuveermh30@gmail.com")
                .password("R@ghumh3017")
                .build();
        Response loginResponse = restClient.postCall(BASE_URL_CONTACTS, CONTACTS_USER_LOGIN_ENDPOINT,
                credentials, null, null, AuthType.NO_AUTH, ContentType.JSON);
        Assert.assertEquals(loginResponse.getStatusCode(), 200, "Login must succeed before tests");
        ConfigManager.setProp("bearerToken", loginResponse.jsonPath().getString("token"));
    }

    @Test(priority = 1)
    public void createContactTest() {
        Contact contact = Contact.builder()
                .firstName("John")
                .lastName("Doe")
                .birthdate("1990-01-15")
                .email("john.doe." + System.currentTimeMillis() + "@test.com")
                .phone("8005551234")
                .street1("1 Main St.")
                .city("Anytown")
                .stateProvince("KS")
                .postalCode("12345")
                .country("US")
                .build();

        Response response = restClient.postCall(BASE_URL_CONTACTS, CONTACTS_ALL_ENDPOINT,
                contact, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        Assert.assertEquals(response.getStatusCode(), 201, "Create contact should return 201");
        Assert.assertNotNull(response.jsonPath().getString("_id"), "Contact id must be returned");
        Assert.assertEquals(response.jsonPath().getString("firstName"), "John");
        Assert.assertEquals(response.jsonPath().getString("lastName"), "Doe");
    }

    @Test(priority = 2)
    public void getSingleContactTest() {
        // Create a contact, then fetch it by ID
        Contact contact = Contact.builder()
                .firstName("Jane")
                .lastName("Smith")
                .birthdate("1992-05-20")
                .email("jane.smith." + System.currentTimeMillis() + "@test.com")
                .phone("8005559876")
                .street1("2 Oak Ave.")
                .city("Springfield")
                .stateProvince("IL")
                .postalCode("62701")
                .country("US")
                .build();

        Response createResponse = restClient.postCall(BASE_URL_CONTACTS, CONTACTS_ALL_ENDPOINT,
                contact, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertEquals(createResponse.getStatusCode(), 201);

        String contactId = createResponse.jsonPath().getString("_id");
        Assert.assertNotNull(contactId, "Contact ID must not be null");

        Response getResponse = restClient.getApiCall(BASE_URL_CONTACTS,
                CONTACTS_ALL_ENDPOINT + "/" + contactId,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        Assert.assertEquals(getResponse.getStatusCode(), 200);
        Assert.assertEquals(getResponse.jsonPath().getString("_id"), contactId);
        Assert.assertEquals(getResponse.jsonPath().getString("firstName"), "Jane");
        Assert.assertEquals(getResponse.jsonPath().getString("country"), "US");
    }

    @Test(priority = 3)
    public void updateContactTest() {
        Contact contact = Contact.builder()
                .firstName("UpdateTest")
                .lastName("User")
                .birthdate("1985-11-20")
                .email("update.test." + System.currentTimeMillis() + "@test.com")
                .phone("8005550001")
                .street1("3 Elm St.")
                .city("Metropolis")
                .stateProvince("NY")
                .postalCode("10001")
                .country("US")
                .build();

        Response createResponse = restClient.postCall(BASE_URL_CONTACTS, CONTACTS_ALL_ENDPOINT,
                contact, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertEquals(createResponse.getStatusCode(), 201);
        String contactId = createResponse.jsonPath().getString("_id");

        // Change only the first name via PUT (full update — must resend all fields)
        contact.setFirstName("UpdatedFirstName");
        Response putResponse = restClient.putApiCall(BASE_URL_CONTACTS,
                CONTACTS_ALL_ENDPOINT + "/" + contactId,
                contact, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);

        Assert.assertEquals(putResponse.getStatusCode(), 200);
        Assert.assertEquals(putResponse.jsonPath().getString("firstName"), "UpdatedFirstName",
                "First name should be updated");
    }

    @Test(priority = 4)
    public void deleteContactTest() {
        Contact contact = Contact.builder()
                .firstName("ToDelete")
                .lastName("User")
                .birthdate("2000-01-01")
                .email("to.delete." + System.currentTimeMillis() + "@test.com")
                .phone("8005550002")
                .street1("4 Pine St.")
                .city("Oldtown")
                .stateProvince("CA")
                .postalCode("90001")
                .country("US")
                .build();

        Response createResponse = restClient.postCall(BASE_URL_CONTACTS, CONTACTS_ALL_ENDPOINT,
                contact, null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertEquals(createResponse.getStatusCode(), 201);
        String contactId = createResponse.jsonPath().getString("_id");

        // This API returns 200 on DELETE (not 204), so we bypass RestClient's responseSpec204or404
        // and call RestAssured directly to assert the correct status.
        Response deleteResponse = RestAssured.given().log().all()
                .baseUri(BASE_URL_CONTACTS)
                .header("Authorization", "Bearer " + ConfigManager.getProp("bearerToken"))
                .delete(CONTACTS_ALL_ENDPOINT + "/" + contactId)
                .then().log().all().extract().response();

        Assert.assertEquals(deleteResponse.getStatusCode(), 200,
                "Contacts API returns 200 on successful DELETE");

        // Verify the contact is actually gone
        Response getAfterDelete = restClient.getApiCall(BASE_URL_CONTACTS,
                CONTACTS_ALL_ENDPOINT + "/" + contactId,
                null, null, AuthType.BEARER_TOKEN, ContentType.JSON);
        Assert.assertEquals(getAfterDelete.getStatusCode(), 404,
                "Deleted contact should return 404");
    }
}
