package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import org.hamcrest.Matcher;
import org.junit.Test;

import java.util.Map;

import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class GatewayAccountFrontendJpaResourceITest extends GatewayAccountResourceTestBase {

    private Gson gson =  new Gson();

    @Test
    public void shouldGetCredentialsForExistingAccount() {
        String accountId = createAGatewayAccountFor("worldpay");
        ImmutableMap<String, String> credentials = ImmutableMap.of("username", "a-username", "password", "a-password", "merchant_id", "a-merchant-id");
        app.getDatabaseTestHelper().updateCredentialsFor(accountId,  gson.toJson(credentials));

        givenSetup().accept(JSON)
                .get(ACCOUNTS_FRONTEND_JPA_URL + accountId)
                .then()
                .statusCode(200)
                .body("payment_provider", is("worldpay"))
                .body("gateway_account_id", is(Integer.parseInt(accountId)))
                .body("credentials.username", is("a-username"))
                .body("credentials.password", is(nullValue()))
                .body("credentials.merchant_id", is("a-merchant-id"));
    }

    @Test
    public void shouldReturn404IfGatewayAccountDoesNotExist() {
        String nonExistingGatewayAccount = "12345";
        givenSetup().accept(JSON)
                .get(ACCOUNTS_FRONTEND_JPA_URL + nonExistingGatewayAccount)
                .then()
                .statusCode(404)
                .body("message", is("Account with id '12345' not found"));

    }

    @Test
    public void shouldUpdateCredentialsForAWorldpayAccountSuccessfully() {
        String accountId = createAGatewayAccountFor("worldpay");

        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("username", "a-username", "password", "a-password", "merchant_id", "a-merchant-id");
        updateCredentialsWith(accountId, expectedCredentials);
    }

    @Test
    public void shouldUpdateCredentialsForASmartpayAccountSuccessfully() {
        String accountId = createAGatewayAccountFor("smartpay");

        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("username", "a-username", "password", "a-password");
        updateCredentialsWith(accountId, expectedCredentials);
    }

    @Test
    public void shouldSaveSpecialCharactersInUserNamesAndPassword() throws Exception {
        String accountId = createAGatewayAccountFor("smartpay");


        String specialUserName = "someone@some{[]where&^%>?\\/";
        String specialPassword = "56g%%Bqv\\>/<wdUpi@#bh{[}]6JV+8w";
        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("username", specialUserName, "password", specialPassword);
        String expectedCredentialsString = gson.toJson(expectedCredentials);

        givenSetup().accept(JSON)
                .body(expectedCredentialsString)
                .put(ACCOUNTS_FRONTEND_JPA_URL + accountId)
                .then()
                .statusCode(200);

        Map<String, String> currentCredentials = app.getDatabaseTestHelper().getAccountCredentials(accountId);
        assertThat(currentCredentials.get("username"), is(specialUserName));
        assertThat(currentCredentials.get("password"), is(specialPassword));
    }

    @Test
    public void shouldFailIfAccountWith2RequiredCredentialsMisses1Credential() {
        String accountId = createAGatewayAccountFor("smartpay");

        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("username", "a-username");
        String expectedCredentialsString = gson.toJson(expectedCredentials);

        givenSetup().accept(JSON)
                .body(expectedCredentialsString)
                .put(ACCOUNTS_FRONTEND_JPA_URL + accountId)
                .then()
                .statusCode(400)
                .body("message", is("The following fields are missing: [password]"));
    }

    @Test
    public void shouldFailIfAccountWith3RequiredCredentialsMisses1Credential() {
        String accountId = createAGatewayAccountFor("worldpay");

        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("username", "a-username", "password", "a-password");
        String expectedCredentialsString = gson.toJson(expectedCredentials);

        givenSetup().accept(JSON)
                .body(expectedCredentialsString)
                .put(ACCOUNTS_FRONTEND_JPA_URL + accountId)
                .then()
                .statusCode(400)
                .body("message", is("The following fields are missing: [merchant_id]"));
    }

    @Test
    public void shouldFailIfAccountWith3RequiredCredentialsMisses2Credentials() {
        String accountId = createAGatewayAccountFor("worldpay");

        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("username", "a-username");
        String expectedCredentialsString = gson.toJson(expectedCredentials);

        givenSetup().accept(JSON)
                .body(expectedCredentialsString)
                .put(ACCOUNTS_FRONTEND_JPA_URL + accountId)
                .then()
                .statusCode(400)
                .body("message", is("The following fields are missing: [password, merchant_id]"));
    }

    @Test
    public void shouldFailIfAccountIdIsNotNumeric() {

        String nonExistingAccountId = "NO_NUMERIC_ACCOUNT_ID";
        assertUpdateInvalidAccountReturnsMessage(nonExistingAccountId, is("The gateway account id 'NO_NUMERIC_ACCOUNT_ID' does not exist"));
    }

    @Test
    public void shouldFailIfAccountIdDoesNotExist() {
        String nonExistingAccountId = "111111111";
        assertUpdateInvalidAccountReturnsMessage(nonExistingAccountId, is("The gateway account id '111111111' does not exist"));
    }

    private void assertUpdateInvalidAccountReturnsMessage(String nonExistingAccountId, Matcher<String> matcher) {
        createAGatewayAccountFor("smartpay");
        ImmutableMap<String, String> expectedCredentials = ImmutableMap.of("username", "a-username", "password", "a-password");
        String expectedCredentialsString = gson.toJson(expectedCredentials);

        givenSetup().accept(JSON)
                .body(expectedCredentialsString)
                .put(ACCOUNTS_FRONTEND_JPA_URL + nonExistingAccountId)
                .then()
                .statusCode(404)
                .body("message", matcher);
    }

    private void updateCredentialsWith(String accountId, ImmutableMap<String, String> expectedCredentials) {

        givenSetup().accept(JSON)
                .body(expectedCredentials)
                .put(ACCOUNTS_FRONTEND_JPA_URL + accountId)
                .then()
                .statusCode(200);

        Map<String, String> currentCredentials = app.getDatabaseTestHelper().getAccountCredentials(accountId);
        assertThat(currentCredentials, is(expectedCredentials));
    }

}
