package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import io.restassured.response.ValidatableResponse;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CreateGatewayAccountResourceIT extends GatewayAccountResourceTestBase {

    GatewayAccountDao gatewayAccountDao;

    @Before
    public void setUp() {
        super.setUp();
        gatewayAccountDao = testContext.getInstanceFromGuiceContainer(GatewayAccountDao.class);
    }

    @Test
    @Parameters({"sandbox", "worldpay", "epdq"})
    public void createAGatewayAccount(String provider) {
        ValidatableResponse response = createAGatewayAccountFor(testContext.getPort(), provider, "my test service", "analytics");
        assertCorrectCreateResponse(response, TEST, "my test service", "analytics", null);
    }

    @Test
    public void createAGatewayAccountWithServiceId() {
        String gatewayAccountId = createAGatewayAccountWithServiceId("some-special-service-id");
        givenSetup()
                .get(ACCOUNTS_API_URL + gatewayAccountId)
                .then()
                .statusCode(200)
                .body("service_id", is("some-special-service-id"));
    }

    @Test
    public void createStripeGatewayAccountWithoutCredentials() {
        Map<String, Object> payload = ImmutableMap.of(
                "type", "test",
                "payment_provider", "stripe",
                "service_name", "My shiny new stripe service");
        givenSetup()
                .body(toJson(payload))
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201)
                .contentType(JSON)
                .body("gateway_account_id", not(isEmptyString()))
                .body("service_name", is("My shiny new stripe service"))
                .body("type", is("test"))
                .body("links.size()", is(1))
                .body("links[0].href", matchesPattern("https://localhost:[0-9]*/v1/api/accounts/[0-9]*"))
                .body("links[0].rel", is("self"))
                .body("links[0].method", is("GET"));
    }

    @Test
    public void createStripeGatewayAccountWithCredentials() {
        String stripeAccountId = "abc";
        Map<String, Object> payload = ImmutableMap.of(
                "type", "test",
                "payment_provider", "stripe",
                "service_name", "My shiny new stripe service",
                "credentials", ImmutableMap.of("stripe_account_id", stripeAccountId));
        String gatewayAccountId = givenSetup()
                .body(toJson(payload))
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201)
                .contentType(JSON)
                .body("gateway_account_id", not(isEmptyString()))
                .body("service_name", is("My shiny new stripe service"))
                .body("type", is("test"))
                .body("links.size()", is(1))
                .body("links[0].href", matchesPattern("https://localhost:[0-9]*/v1/api/accounts/[0-9]*"))
                .body("links[0].rel", is("self"))
                .body("links[0].method", is("GET"))
                .extract()
                .body()
                .jsonPath()
                .getString("gateway_account_id");
        Optional<GatewayAccountEntity> gatewayAccount = gatewayAccountDao.findById(Long.valueOf(gatewayAccountId));
        assertThat(gatewayAccount.isPresent(), is(true));

        List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsList = gatewayAccount.get().getGatewayAccountCredentials();
        assertThat(gatewayAccountCredentialsList.size(), is(1));
        GatewayCredentials credentialsObject = gatewayAccountCredentialsList.get(0).getCredentialsObject();
        assertThat(credentialsObject, isA(StripeCredentials.class));
        assertThat(((StripeCredentials)credentialsObject).getStripeAccountId(), is(stripeAccountId));
        assertThat(gatewayAccountCredentialsList.get(0).getCredentials().get("stripe_account_id"), is(stripeAccountId));
        assertThat(gatewayAccountCredentialsList.get(0).getState(), is(ACTIVE));
        assertThat(gatewayAccountCredentialsList.get(0).getPaymentProvider(), is("stripe"));
        assertThat(gatewayAccountCredentialsList.get(0).getActiveStartDate(), is(notNullValue()));
    }

    @Test
    public void createGatewayAccountWithoutPaymentProviderDefaultsToSandbox() {
        String payload = toJson(ImmutableMap.of("name", "test account", "type", "test"));

        ValidatableResponse response = givenSetup()
                .body(payload)
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201);

        assertCorrectCreateResponse(response, TEST);
        assertGettingAccountReturnsProviderName(testContext.getPort(), response, "sandbox", TEST);

        String gatewayAccountId = response.extract()
                .body()
                .jsonPath()
                .getString("gateway_account_id");
        Optional<GatewayAccountEntity> gatewayAccount = gatewayAccountDao.findById(Long.valueOf(gatewayAccountId));
        assertThat(gatewayAccount.isPresent(), is(true));

        List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsList = gatewayAccount.get().getGatewayAccountCredentials();
        assertThat(gatewayAccountCredentialsList.size(), is(1));
        assertThat(gatewayAccountCredentialsList.get(0).getState(), is(ACTIVE));
        assertThat(gatewayAccountCredentialsList.get(0).getPaymentProvider(), is("sandbox"));
        assertThat(gatewayAccountCredentialsList.get(0).getCredentials().isEmpty(), is(true));
    }

    @Test
    public void createGatewayAccount_shouldNotReturnCorporateSurcharges() {
        String payload = toJson(ImmutableMap.of("name", "test account"));
        ValidatableResponse response = givenSetup()
                .body(payload)
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201);

        response.body("corporate_credit_card_surcharge_amount", is(nullValue()));
        response.body("corporate_debit_card_surcharge_amount", is(nullValue()));
        response.body("corporate_prepaid_debit_card_surcharge_amount", is(nullValue()));
    }

    @Test
    public void createGatewayAccountWithProviderUrlTypeLive() {
        String payload = toJson(ImmutableMap.of("payment_provider", "worldpay", "type", LIVE.toString()));
        ValidatableResponse response = givenSetup()
                .body(payload)
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201);

        assertCorrectCreateResponse(response, LIVE);
        assertGettingAccountReturnsProviderName(testContext.getPort(), response, "worldpay", LIVE);
    }

    @Test
    public void createGatewayAccountWithRequires3dsToTrue() {
        String payload = toJson(ImmutableMap.of("payment_provider", "stripe", "type", LIVE.toString(), "requires_3ds", "true"));
        ValidatableResponse response = givenSetup()
                .body(payload)
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201);

        assertCorrectCreateResponse(response, LIVE);

        response.body("requires_3ds", is(true));
    }

    @Test
    public void createGatewayAccountWithRequires3dsDefaultsToFalse() {
        String payload = toJson(ImmutableMap.of("payment_provider", "worldpay", "type", LIVE.toString()));
        ValidatableResponse response = givenSetup()
                .body(payload)
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201);

        assertCorrectCreateResponse(response, LIVE);

        response.body("requires_3ds", is(false));
    }

    @Test
    public void createGatewayAccountWithNameDescriptionAndAnalyticsId() {
        String payload = toJson(ImmutableMap.of("service_name", "my service name", "description", "desc", "analytics_id", "analytics-id"));
        ValidatableResponse response = givenSetup()
                .body(payload)
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201);

        assertCorrectCreateResponse(response, TEST, "desc", "analytics-id", "my service name");
        assertGettingAccountReturnsProviderName(testContext.getPort(), response, "sandbox", TEST);
    }

    @Test
    public void createGatewayAccountWithMissingProviderUrlTypeCreatesTestType() {
        String payload = toJson(ImmutableMap.of("payment_provider", "worldpay"));
        ValidatableResponse response = givenSetup()
                .body(payload)
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201);

        assertCorrectCreateResponse(response, TEST);
        assertGettingAccountReturnsProviderName(testContext.getPort(), response, "worldpay", TEST);
    }

    private void assertCorrectCreateResponse(ValidatableResponse response, GatewayAccountType type) {
        assertCorrectCreateResponse(response, type, null, null, null);
    }
}
