package uk.gov.pay.connector.it.resources.stripe;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.Response;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.stripe.StripeNotificationType;
import uk.gov.pay.connector.gateway.stripe.StripeNotificationUtilTest;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CANCELED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CHARGEABLE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_FAILED;
import static uk.gov.pay.connector.junit.DropwizardJUnitRunner.WIREMOCK_PORT;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_3DS_SOURCE;
import static uk.gov.pay.connector.util.TransactionId.randomId;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class StripeNotificationResourceITest {

    private static final String NOTIFICATION_PATH = "/v1/api/notifications/stripe";
    private static final String RESPONSE_EXPECTED_BY_STRIPE = "[OK]";

    private RestAssuredClient connectorRestApiClient;

    private String paymentProvider = PaymentGatewayName.STRIPE.getName();
    private String accountId;
    private DatabaseTestHelper databaseTestHelper;
    @DropwizardTestContext
    private TestContext testContext;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WIREMOCK_PORT);

    StripeMockClient stripeMockClient;

    @Before
    public void setup() {
        accountId = String.valueOf(RandomUtils.nextInt());

        databaseTestHelper = testContext.getDatabaseTestHelper();
        databaseTestHelper.addGatewayAccount(accountId, "stripe", ImmutableMap.of("stripe_account_id", "stripe_account_id"));
        connectorRestApiClient = new RestAssuredClient(testContext.getPort(), accountId);

        stripeMockClient = new StripeMockClient();
    }

    @Test
    public void shouldHandleASourceChargeableNotification() {
        String transactionId = "transaction-id" + nextInt();
        String externalChargeId = createNewChargeWith(AUTHORISATION_3DS_REQUIRED, transactionId);
        stripeMockClient.mockCreateCharge();

        String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                transactionId, SOURCE_CHARGEABLE);
        String response = notifyConnector(payload)
                .then()
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_STRIPE));

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldHandleASourceFailedNotification() {
        String transactionId = "transaction-id" + nextInt();
        String externalChargeId = createNewChargeWith(AUTHORISATION_3DS_REQUIRED, transactionId);

        String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                transactionId, SOURCE_FAILED);
        String response = notifyConnector(payload)
                .then()
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_STRIPE));

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    public void shouldHandleASourceCanceledNotification() {
        String transactionId = "transaction-id" + nextInt();
        String externalChargeId = createNewChargeWith(AUTHORISATION_3DS_REQUIRED, transactionId);

        String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                transactionId, SOURCE_CANCELED);
        String response = notifyConnector(payload)
                .then()
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_STRIPE));

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_CANCELLED.getValue());
    }

    @Test
    public void shouldFailAStripeNotificationWithAnUnexpectedContentType() {
        String transactionId = randomId();
        createNewChargeWith(AUTHORISATION_SUCCESS, transactionId);

        given()
                .port(testContext.getPort())
                .body("")
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(415);
    }

    @Test
    public void shouldFailAStripeNotification_whenSignatureIsInvalid() {
        String transactionId = "transaction-id" + nextInt();
        String externalChargeId = createNewChargeWith(AUTHORISATION_3DS_REQUIRED, transactionId);

        String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                transactionId, SOURCE_CANCELED);
        String response = notifyConnectorWithHeader(payload, "invalid-header")
                .then()
                .statusCode(500)
                .extract().body()
                .asString();

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_3DS_REQUIRED.getValue());
    }

    private Response notifyConnectorWithHeader(String payload, String header) {
        return given()
                .port(testContext.getPort())
                .body(payload)
                .header("Stripe-Signature", header)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH);
    }

    private Response notifyConnector(String payload) {
        return notifyConnectorWithHeader(payload, StripeNotificationUtilTest.generateSigHeader(
                "whsec", payload));
    }

    private static String sampleStripeNotification(String location,
                                                   String sourceId,
                                                   StripeNotificationType stripeNotificationType) {
        return TestTemplateResourceLoader.load(location)
                .replace("{{sourceId}}", sourceId)
                .replace("{{type}}", stripeNotificationType.getType());
    }

    protected String createNewChargeWith(ChargeStatus status, String gatewayTransactionId) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, 6234L, status, "RETURN_URL", gatewayTransactionId);
        return externalChargeId;
    }

    protected void assertFrontendChargeStatusIs(String chargeId, String status) {
        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status));
    }
}
