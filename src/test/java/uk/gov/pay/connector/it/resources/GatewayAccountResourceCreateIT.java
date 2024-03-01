package uk.gov.pay.connector.it.resources;

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
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayAccountResourceCreateIT extends GatewayAccountResourceTestBase {

    GatewayAccountDao gatewayAccountDao;

    @Before
    public void setUp() {
        super.setUp();
        gatewayAccountDao = testContext.getInstanceFromGuiceContainer(GatewayAccountDao.class);
    }

    @Test
    public void createASandboxGatewayAccount() {
        ValidatableResponse response = createAGatewayAccountFor(testContext.getPort(), "sandbox", "my test service", "analytics");
        assertCorrectCreateResponse(response, TEST, "my test service", "analytics", null);
    }

    @Test
    public void createAWorldpaySandboxGatewayAccount() {
        ValidatableResponse response = createAGatewayAccountFor(testContext.getPort(), "worldpay", "my test service", "analytics");
        assertCorrectCreateResponse(response, TEST, "my test service", "analytics", null);
    }

    @Test
    public void createAWorldpayStripeGatewayAccount() {
        ValidatableResponse response = createAGatewayAccountFor(testContext.getPort(), "stripe", "my test service", "analytics");
        assertCorrectCreateResponse(response, TEST, "my test service", "analytics", null);
    }

    @Test
    public void shouldCreateAccountWithServiceIdAndDefaultsForOtherProperties() {
        Map<String, Object> payload = Map.of(
                "service_id", "some-special-service-id");
        ValidatableResponse response = givenSetup()
                .body(toJson(payload))
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201)
                .contentType(JSON)
                .body("gateway_account_id", not(emptyString()))
                .body("type", is("test"))
                .body("requires_3ds", is(false));

        givenSetup()
                .get(response.extract().header("Location").replace("https", "http")) //Scheme on links back are forced to be https
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("payment_provider", is("sandbox"))
                .body("gateway_account_id", is(notNullValue()))
                .body("type", is("test"))
                .body("requires3ds", is(false))
                .body("allow_apple_pay", is(true))
                .body("allow_google_pay", is(false))
                .body("corporate_credit_card_surcharge_amount", is(0))
                .body("corporate_debit_card_surcharge_amount", is(0))
                .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
    }

    @Test
    public void shouldCreateAccountWithAllConfigurationOptions() {
        Map<String, Object> payload = Map.of(
                "service_id", "some-special-service-id",
                "service_name", "a-service-name",
                "type", "live",
                "payment_provider", "stripe",
                "description", "a-description",
                "analytics_id", "an-analytics-id",
                "requires_3ds", true,
                "allow_apple_pay", true,
                "allow_google_pay", true
        );
        ValidatableResponse response = givenSetup()
                .body(toJson(payload))
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201)
                .contentType(JSON)
                .body("gateway_account_id", not(emptyString()))
                .body("service_name", is("a-service-name"))
                .body("type", is("live"))
                .body("requires_3ds", is(true))
                .body("analytics_id", is("an-analytics-id"))
                .body("description", is("a-description"));

        givenSetup()
                .get(response.extract().header("Location").replace("https", "http")) //Scheme on links back are forced to be https
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("payment_provider", is("stripe"))
                .body("gateway_account_id", is(notNullValue()))
                .body("service_name", is("a-service-name"))
                .body("service_id", is("some-special-service-id"))
                .body("type", is("live"))
                .body("analytics_id", is("an-analytics-id"))
                .body("description", is("a-description"))
                .body("requires3ds", is(true))
                .body("allow_apple_pay", is(true))
                .body("allow_google_pay", is(true));
    }

    @Test
    public void createStripeGatewayAccountWithoutCredentials() {
        Map<String, Object> payload = Map.of(
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
        Map<String, Object> payload = Map.of(
                "type", "test",
                "payment_provider", "stripe",
                "service_name", "My shiny new stripe service",
                "credentials", Map.of("stripe_account_id", stripeAccountId));
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
        assertThat(((StripeCredentials) credentialsObject).getStripeAccountId(), is(stripeAccountId));
        assertThat(gatewayAccountCredentialsList.get(0).getCredentials().get("stripe_account_id"), is(stripeAccountId));
        assertThat(gatewayAccountCredentialsList.get(0).getState(), is(ACTIVE));
        assertThat(gatewayAccountCredentialsList.get(0).getPaymentProvider(), is("stripe"));
        assertThat(gatewayAccountCredentialsList.get(0).getActiveStartDate(), is(notNullValue()));
    }

    @Test
    public void createGatewayAccountWithoutPaymentProviderDefaultsToSandbox() {
        String payload = toJson(Map.of("name", "test account", "type", "test"));

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
    
    private void assertCorrectCreateResponse(ValidatableResponse response, GatewayAccountType type) {
        assertCorrectCreateResponse(response, type, null, null, null);
    }

}
