package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jayway.restassured.http.ContentType;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class GatewayAccountResourceCredentialsITest extends GatewayAccountResourceTestBase {

    private Gson gson =  new Gson();

    @Test
    public void shouldUpdateCredentialsForAWorldpayAccountSuccessfully() {
        String accountId = createAGatewayAccountFor("worldpay");

        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("username", "a-username", "password", "a-password", "merchant_id", "a-merchant-id");
        String expectedCredentialsString = gson.toJson(expectedCredentials);

        givenSetup().accept(ContentType.JSON)
                .body(expectedCredentialsString)
                .put(ACCOUNTS_FRONTEND_URL + accountId)
                .then()
                .statusCode(200);

        JsonObject currentCredentials = app.getDatabaseTestHelper().getAccountCredentials(accountId);
        assertThat(currentCredentials, is(gson.fromJson(expectedCredentialsString, JsonObject.class)));
    }

    @Test
    public void shouldUpdateCredentialsForASmartpayAccountSuccessfully() {
        String accountId = createAGatewayAccountFor("smartpay");

        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("username", "a-username", "password", "a-password");
        String expectedCredentialsString = gson.toJson(expectedCredentials);

        givenSetup().accept(ContentType.JSON)
                .body(expectedCredentialsString)
                .put(ACCOUNTS_FRONTEND_URL + accountId)
                .then()
                .statusCode(200);

        JsonObject currentCredentials = app.getDatabaseTestHelper().getAccountCredentials(accountId);
        assertThat(currentCredentials, is(gson.fromJson(expectedCredentialsString, JsonObject.class)));
    }

    @Test
    public void shouldFailIfNoUsernameInRequestPayload() {
        String accountId = createAGatewayAccountFor("smartpay");

        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("password", "a-password");
        String expectedCredentialsString = gson.toJson(expectedCredentials);

        givenSetup().accept(ContentType.JSON)
                .body(expectedCredentialsString)
                .put(ACCOUNTS_FRONTEND_URL + accountId)
                .then()
                .statusCode(400)
                .body("message", is("The following fields are missing: [username]"));
    }

    @Test
    public void shouldFailIfNoPasswordInRequestPayload() {
        String accountId = createAGatewayAccountFor("smartpay");

        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("username", "a-username");
        String expectedCredentialsString = gson.toJson(expectedCredentials);

        givenSetup().accept(ContentType.JSON)
                .body(expectedCredentialsString)
                .put(ACCOUNTS_FRONTEND_URL + accountId)
                .then()
                .statusCode(400)
                .body("message", is("The following fields are missing: [password]"));
    }

    @Test
    public void shouldFailIfNoUsernameAndPasswordInRequestPayload() {
        String accountId = createAGatewayAccountFor("smartpay");

        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("other-field", "other-value");
        String expectedCredentialsString = gson.toJson(expectedCredentials);

        givenSetup().accept(ContentType.JSON)
                .body(expectedCredentialsString)
                .put(ACCOUNTS_FRONTEND_URL + accountId)
                .then()
                .statusCode(400)
                .body("message", is("The following fields are missing: [username,password]"));
    }

    @Test
    public void shouldFailIfAccountIdIsNotNumeric() {
        createAGatewayAccountFor("smartpay");

        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("username", "a-username", "password", "a-password");
        String expectedCredentialsString = gson.toJson(expectedCredentials);

        String nonExistingAccountId = "NO_NUMERIC_ACCOUNT_ID";
        givenSetup().accept(ContentType.JSON)
                .body(expectedCredentialsString)
                .put(ACCOUNTS_FRONTEND_URL + nonExistingAccountId)
                .then()
                .statusCode(404)
                .body("message", is("The gateway account id 'NO_NUMERIC_ACCOUNT_ID' does not exist"));
    }

    @Test
    public void shouldFailIfAccountIdDoesNotExist() {
        createAGatewayAccountFor("smartpay");

        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("username", "a-username", "password", "a-password");
        String expectedCredentialsString = gson.toJson(expectedCredentials);

        String nonExistingAccountId = "111111111";
        givenSetup().accept(ContentType.JSON)
                .body(expectedCredentialsString)
                .put(ACCOUNTS_FRONTEND_URL + nonExistingAccountId)
                .then()
                .statusCode(404)
                .body("message", is("The gateway account id '111111111' does not exist"));
    }

}
