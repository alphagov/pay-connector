package uk.gov.pay.connector.it.resources;

import com.google.common.collect.Maps;
import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Before;
import uk.gov.pay.connector.it.IntegrationDropwizardITestSuite;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresTemplateRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountResourceTestBase extends ChargingITestBase {

    public static final String ACCOUNTS_API_URL = "/v1/api/accounts/";
    public static final String ACCOUNTS_FRONTEND_URL = "/v1/frontend/accounts/";
    protected DatabaseTestHelper databaseTestHelper;

    public DropwizardAppWithPostgresTemplateRule app = IntegrationDropwizardITestSuite.getApp();

    protected DatabaseFixtures databaseFixtures;

    public GatewayAccountResourceTestBase() {
        super("sandbox");
    }

    @Before
    public void setUp() {
        databaseTestHelper = app.getDatabaseTestHelper();
        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
    }

    protected RequestSpecification givenSetup() {
        return given().port(app.getLocalPort())
                .contentType(JSON);
    }

    //TODO remove this after complete migration
    String createAGatewayAccountFor(String testProvider) {
       return createAGatewayAccountFor(testProvider, null, null);
    }

    String createAGatewayAccountFor(String testProvider, String description, String analyticsId) {
        Map<String, String> payload = Maps.newHashMap();
        payload.put("payment_provider", testProvider);
        if (description != null) {
            payload.put("description", description);
        }
        if (analyticsId != null) {
            payload.put( "analytics_id", analyticsId);
        }
        ValidatableResponse response = givenSetup()
                .body(toJson(payload))
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201)
                .contentType(JSON);

        assertCorrectCreateResponse(response, GatewayAccountEntity.Type.TEST, description, analyticsId, null);
        assertGettingAccountReturnsProviderName(response, testProvider, GatewayAccountEntity.Type.TEST);
        assertGatewayAccountCredentialsAreEmptyInDB(response);
        assertGatewayAccountCredentialsAreEmptyInDB(response);
        assertGatewayAccountCredentialsAreEmptyInDB(response);
        return response.extract().path("gateway_account_id");
    }
    void assertGettingAccountReturnsProviderName(ValidatableResponse response, String providerName, GatewayAccountEntity.Type providerUrlType) {
        givenSetup()
                .get(response.extract().header("Location").replace("https", "http")) //Scheme on links back are forced to be https
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("payment_provider", is(providerName))
                .body("gateway_account_id", is(notNullValue()))
                .body("type", is(providerUrlType.toString()));
    }

    void assertCorrectCreateResponse(ValidatableResponse response) {
        assertCorrectCreateResponse(response, GatewayAccountEntity.Type.TEST);
    }

    void assertCorrectCreateResponse(ValidatableResponse response, GatewayAccountEntity.Type type) {
        assertCorrectCreateResponse(response, type, null, null, null);
    }
    void assertCorrectCreateResponse(ValidatableResponse response, GatewayAccountEntity.Type type, String description, String analyticsId, String name) {
        String accountId = response.extract().path("gateway_account_id");
        String urlSlug = "api/accounts/" + accountId;

        response.header("Location", containsString(urlSlug))
                .body("gateway_account_id", containsString(accountId))
                .body("type", is(type.toString()))
                .body("description", is(description))
                .body("service_name", is(name))
                .body("analytics_id", is(analyticsId))
                .body("links[0].href", containsString(urlSlug))
                .body("links[0].rel", is("self"))
                .body("links[0].method", is("GET"));
    }
    private void assertGatewayAccountCredentialsAreEmptyInDB(ValidatableResponse response) {
        String gateway_account_id = response.extract().path("gateway_account_id");
        Map<String, String> accountCredentials = app.getDatabaseTestHelper().getAccountCredentials(Long.valueOf(gateway_account_id));
        assertThat(accountCredentials, is(new HashMap<>()));
    }

    DatabaseFixtures.TestCardType createMastercardCreditCardTypeRecord() {
        return databaseFixtures.aMastercardCreditCardType().insert();
    }

    DatabaseFixtures.TestCardType createVisaDebitCardTypeRecord() {
        return databaseFixtures.aVisaDebitCardType().insert();
    }

    DatabaseFixtures.TestCardType createVisaCreditCardTypeRecord() {
        return databaseFixtures.aVisaCreditCardType().insert();
    }

    DatabaseFixtures.TestAccount createAccountRecord(DatabaseFixtures.TestCardType... cardTypes) {
        return databaseFixtures
                .aTestAccount()
                .withCardTypes(Arrays.asList(cardTypes))
                .insert();
    }
}
