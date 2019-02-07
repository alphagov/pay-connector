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
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CreateGatewayAccountResourceTest extends GatewayAccountResourceTestBase {

    GatewayAccountDao gatewayAccountDao;

    @Before
    public void setUp() {
        super.setUp();
        gatewayAccountDao = testContext.getInstanceFromGuiceContainer(GatewayAccountDao.class);
    }

    @Test
    @Parameters({"sandbox", "worldpay", "smartpay", "epdq"})
    public void createAGatewayAccount(String provider) {
        createAGatewayAccountFor(testContext.getPort(), provider, "my test service", "analytics", databaseTestHelper);
    }

    @Test
    public void createStripeGatewayAccountWithoutCredentials() throws Exception {
        Map<String, Object> payload = ImmutableMap.of(
                "type", "test",
                "payment_provider", "stripe",
                "service_name", "My shiny new stripe service");
        given().port(testContext.getPort()).contentType(JSON)
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
    public void createStripeGatewayAccountWithCredentials() throws Exception {
        Map<String, Object> payload = ImmutableMap.of(
                "type", "test",
                "payment_provider", "stripe",
                "service_name", "My shiny new stripe service",
                "credentials", ImmutableMap.of("stripe_account_id", "abc"));
        String gatewayAccountId = given().port(testContext.getPort()).contentType(JSON)
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
        assertThat(gatewayAccount.get().getCredentials().get("stripe_account_id"), is("abc"));
    }

    @Test
    public void createGatewayAccountWithoutPaymentProviderDefaultsToSandbox() {
        String payload = toJson(ImmutableMap.of("name", "test account", "type", "test"));

        ValidatableResponse response = given().port(testContext.getPort()).contentType(JSON)
                .body(payload)
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201);

        assertCorrectCreateResponse(response);
        assertGettingAccountReturnsProviderName(testContext.getPort(), response, "sandbox", TEST);
    }

    @Test
    public void createGatewayAccount_shouldNotReturnCorporateSurcharges() {
        String payload = toJson(ImmutableMap.of("name", "test account"));
        ValidatableResponse response = given().port(testContext.getPort()).contentType(JSON)
                .body(payload)
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201);

        response.body("corporate_credit_card_surcharge_amount", is(nullValue()));
        response.body("corporate_debit_card_surcharge_amount", is(nullValue()));
        response.body("corporate_prepaid_credit_card_surcharge_amount", is(nullValue()));
        response.body("corporate_prepaid_debit_card_surcharge_amount", is(nullValue()));
    }

    @Test
    public void createGatewayAccountWithProviderUrlTypeLive() {
        String payload = toJson(ImmutableMap.of("payment_provider", "worldpay", "type", LIVE.toString()));
        ValidatableResponse response = given().port(testContext.getPort()).contentType(JSON)
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
        ValidatableResponse response = given().port(testContext.getPort()).contentType(JSON)
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
        ValidatableResponse response = given().port(testContext.getPort()).contentType(JSON)
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
        ValidatableResponse response = given().port(testContext.getPort()).contentType(JSON)
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
        ValidatableResponse response = given().port(testContext.getPort()).contentType(JSON)
                .body(payload)
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201);

        assertCorrectCreateResponse(response, TEST);
        assertGettingAccountReturnsProviderName(testContext.getPort(), response, "worldpay", TEST);
    }
}
