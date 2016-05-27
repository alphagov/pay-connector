package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Before;
import org.junit.Rule;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountResourceTestBase {

    public static final String ACCOUNTS_API_URL = "/v1/api/accounts/";
    public static final String ACCOUNTS_FRONTEND_URL = "/v1/frontend/accounts/";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();
    protected DatabaseFixtures databaseFixtures;

    @Before
    public void setUp() {
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(app.getDatabaseTestHelper());
    }

    protected RequestSpecification givenSetup() {
        return given().port(app.getLocalPort())
                .contentType(JSON);
    }

    //TODO remove this after complete migration
    protected String createAGatewayAccountFor(String testProvider) {
        ValidatableResponse response = givenSetup()
                .body(toJson(ImmutableMap.of("payment_provider", testProvider)))
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201)
                .contentType(JSON);

        assertCorrectAccountLocationIn(response);

        assertGettingAccountReturnsProviderName(response, testProvider);

        assertGatewayAccountCredentialsAreEmptyInDB(response);

        return response.extract().path("gateway_account_id");
    }

    protected void assertGettingAccountReturnsProviderName(ValidatableResponse response, String providerName) {
        givenSetup()
                .get(response.extract().header("Location"))
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("payment_provider", is(providerName))
                .body("gateway_account_id", is(notNullValue()));
    }

    protected void assertCorrectAccountLocationIn(ValidatableResponse response) {
        String accountId = response.extract().path("gateway_account_id");
        String urlSlug = "api/accounts/" + accountId;

        response.header("Location", containsString(urlSlug))
                .body("gateway_account_id", containsString(accountId))
                .body("links[0].href", containsString(urlSlug))
                .body("links[0].rel", is("self"))
                .body("links[0].method", is("GET"));
    }

    private void assertGatewayAccountCredentialsAreEmptyInDB(ValidatableResponse response) {
        String gateway_account_id = response.extract().path("gateway_account_id");
        Map<String, String> accountCredentials = app.getDatabaseTestHelper().getAccountCredentials(Long.valueOf(gateway_account_id));
        assertThat(accountCredentials, is(new HashMap<>()));
    }

    protected DatabaseFixtures.TestCardType createMastercardCreditCardTypeRecord() {
        return databaseFixtures.aMastercardCreditCardType().insert();
    }

    protected DatabaseFixtures.TestCardType createVisaDebitCardTypeRecord() {
        return databaseFixtures.aVisaDebitCardType().insert();
    }

    protected DatabaseFixtures.TestCardType createVisaCreditCardTypeRecord() {
        return databaseFixtures.aVisaCreditCardType().insert();
    }

    protected DatabaseFixtures.TestAccount createAccountRecord(DatabaseFixtures.TestCardType... cardTypes) {
        return databaseFixtures
                .aTestAccount()
                .withCardTypes(Arrays.asList(cardTypes))
                .insert();
    }
}
