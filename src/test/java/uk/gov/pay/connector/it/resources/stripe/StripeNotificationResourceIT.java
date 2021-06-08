package uk.gov.pay.connector.it.resources.stripe;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.response.Response;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
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

import java.util.Map;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_PAYMENT_FAILED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CANCELED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CHARGEABLE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_FAILED;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_3DS_SOURCE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_PAYMENT_INTENT;
import static uk.gov.pay.connector.util.TransactionId.randomId;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(
        app = ConnectorApp.class,
        config = "config/test-it-config.yaml")
public class StripeNotificationResourceIT {

    private static final String NOTIFICATION_PATH = "/v1/api/notifications/stripe";
    private static final String RESPONSE_EXPECTED_BY_STRIPE = "[OK]";
    private static final String STRIPE_IP_ADDRESS = "1.2.3.4";
    private static final String UNEXPECTED_IP_ADDRESS = "1.1.1.1";

    private RestAssuredClient connectorRestApiClient;

    private String accountId;
    private DatabaseTestHelper databaseTestHelper;
    @DropwizardTestContext
    private TestContext testContext;

    private WireMockServer wireMockServer;

    private StripeMockClient stripeMockClient;

    @Before
    public void setup() {
        accountId = String.valueOf(RandomUtils.nextInt());

        databaseTestHelper = testContext.getDatabaseTestHelper();
        wireMockServer = testContext.getWireMockServer();
        var gatewayAccountParams = anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway("stripe")
                .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);
        connectorRestApiClient = new RestAssuredClient(testContext.getPort(), accountId);

        stripeMockClient = new StripeMockClient(wireMockServer);
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
    public void shouldHandleAPaymentIntentAmountCapturableUpdatedNotification() {
        String transactionId = "pi_123" + nextInt();
        String externalChargeId = createNewChargeWith(AUTHORISATION_3DS_REQUIRED, transactionId);

        String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                transactionId, PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED);
        String response = notifyConnector(payload)
                .then()
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_STRIPE));

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldHandleAPaymentIntentPaymentFailedNotification() {
        String transactionId = "pi_123" + nextInt();
        String externalChargeId = createNewChargeWith(AUTHORISATION_3DS_REQUIRED, transactionId);

        String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                transactionId, PAYMENT_INTENT_PAYMENT_FAILED);
        String response = notifyConnector(payload)
                .then()
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_STRIPE));

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_REJECTED.getValue());
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
        notifyConnectorWithHeader(payload, "invalid-header")
                .then()
                .statusCode(500)
                .extract().body()
                .asString();

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_3DS_REQUIRED.getValue());
    }

    @Test
    public void shouldReturnForbiddenIfRequestComesFromUnexpectedIpAddress() {
        String transactionId = "transaction-id" + nextInt();
        String externalChargeId = createNewChargeWith(AUTHORISATION_3DS_REQUIRED, transactionId);
        stripeMockClient.mockCreateCharge();

        String payload = sampleStripeNotification(STRIPE_NOTIFICATION_3DS_SOURCE,
                transactionId, SOURCE_CHARGEABLE);

        notifyConnectorFromUnexpectedIpAddress(payload)
                .then()
                .statusCode(403);
    }

    private Response notifyConnectorWithHeader(String payload, String header) {
        return given()
                .port(testContext.getPort())
                .body(payload)
                .header("Stripe-Signature", header)
                .header("X-Forwarded-For", STRIPE_IP_ADDRESS)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH);
    }

    private Response notifyConnectorWithHeader(String payload, String stripeSignature, String forwardedIpAddresses) {
        return given()
                .port(testContext.getPort())
                .body(payload)
                .header("Stripe-Signature", stripeSignature)
                .header("X-Forwarded-For", forwardedIpAddresses)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH);
    }

    private Response notifyConnector(String payload) {
        return notifyConnectorWithHeader(payload, StripeNotificationUtilTest.generateSigHeader(
                "whtest", payload));
    }

    private Response notifyConnectorFromUnexpectedIpAddress(String payload) {
        return notifyConnectorWithHeader(payload, StripeNotificationUtilTest.generateSigHeader(
                "whtest", payload), UNEXPECTED_IP_ADDRESS);
    }

    private static String sampleStripeNotification(String location,
                                                   String sourceId,
                                                   StripeNotificationType stripeNotificationType) {
        return TestTemplateResourceLoader.load(location)
                .replace("{{id}}", sourceId)
                .replace("{{type}}", stripeNotificationType.getType());
    }

    protected String createNewChargeWith(ChargeStatus status, String gatewayTransactionId) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(1000L)
                .withStatus(status)
                .withTransactionId(gatewayTransactionId)
                .build());
        return externalChargeId;
    }

    protected void assertFrontendChargeStatusIs(String chargeId, String status) {
        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status));
    }
}
