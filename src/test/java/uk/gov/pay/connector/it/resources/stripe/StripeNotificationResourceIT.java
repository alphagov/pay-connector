package uk.gov.pay.connector.it.resources.stripe;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.stripe.StripeNotificationType;
import uk.gov.pay.connector.gateway.stripe.StripeNotificationUtilTest;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_XML;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_PAYMENT_FAILED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.randomInt;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_PAYMENT_INTENT;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class StripeNotificationResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    private static final String NOTIFICATION_PATH = "/v1/api/notifications/stripe";
    private static final String RESPONSE_EXPECTED_BY_STRIPE = "[OK]";
    private static final String STRIPE_IP_ADDRESS = "1.2.3.4";
    private static final String UNEXPECTED_IP_ADDRESS = "1.1.1.1";
    private static final String ISSUER_URL = "http://stripe.url/3ds";

    private RestAssuredClient connectorRestApiClient;

    private String accountId;
    private DatabaseTestHelper databaseTestHelper;

    private WireMockServer wireMockServer;

    private StripeMockClient stripeMockClient;

    private AddGatewayAccountCredentialsParams accountCredentialsParams;

    @BeforeEach
    void setup() {
        accountId = String.valueOf(randomInt());

        databaseTestHelper = app.getDatabaseTestHelper();
        wireMockServer = app.getWireMockServer();
        accountCredentialsParams = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(STRIPE.getName())
                .withGatewayAccountId(Long.valueOf(accountId))
                .withState(ACTIVE)
                .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                .build();
        var gatewayAccountParams = anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway("stripe")
                .withGatewayAccountCredentials(List.of(accountCredentialsParams))
                .build();
        databaseTestHelper.addGatewayAccount(gatewayAccountParams);
        connectorRestApiClient = new RestAssuredClient(app.getLocalPort(), accountId);

        stripeMockClient = new StripeMockClient(wireMockServer);
    }

    @Test
    void shouldHandleAPaymentIntentAmountCapturableUpdatedNotification() {
        String transactionId = "pi_123" + randomInt();
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
    void shouldHandleAPaymentIntentPaymentFailedNotification() {
        String transactionId = "pi_123" + randomInt();
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
    void shouldFailAStripeNotificationWithAnUnexpectedContentType() {
        String transactionId = randomId();
        createNewChargeWith(AUTHORISATION_SUCCESS, transactionId);

        given()
                .port(app.getLocalPort())
                .body("")
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(415);
    }

    @Test
    void shouldFailAStripeNotification_whenSignatureIsInvalid() {
        String transactionId = "transaction-id" + randomInt();
        String externalChargeId = createNewChargeWith(AUTHORISATION_3DS_REQUIRED, transactionId);

        String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                transactionId, PAYMENT_INTENT_PAYMENT_FAILED);
        notifyConnectorWithHeader(payload, "invalid-header")
                .then()
                .statusCode(500)
                .extract().body()
                .asString();

        assertFrontendChargeStatusIs(externalChargeId, AUTHORISATION_3DS_REQUIRED.getValue());
    }

    @Test
    void shouldReturnForbiddenIfRequestComesFromUnexpectedIpAddress() {
        String transactionId = "transaction-id" + randomInt();
        createNewChargeWith(AUTHORISATION_3DS_REQUIRED, transactionId);
        stripeMockClient.mockCreatePaymentIntent();

        String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                transactionId, PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED);

        notifyConnectorFromUnexpectedIpAddress(payload)
                .then()
                .statusCode(403);
    }

    @Test
    void shouldHandleAPaymentIntent3DSVersion() {
        String transactionId = "pi_123" + randomInt();
        String externalChargeId = createNewChargeWith(AUTHORISATION_3DS_REQUIRED, transactionId);

        String payload = sampleStripeNotification(STRIPE_NOTIFICATION_PAYMENT_INTENT,
                transactionId, PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED);
        notifyConnector(payload)
                .then()
                .statusCode(200);

        Map<String, Object> charge = databaseTestHelper.getChargeByExternalId(externalChargeId);

        assertThat(charge.get("version_3ds").toString(), is("2.0.1"));
        assertThat(charge.get("issuer_url_3ds").toString(), is(ISSUER_URL));
    }

    private Response notifyConnectorWithHeader(String payload, String header) {
        return given()
                .port(app.getLocalPort())
                .body(payload)
                .header("Stripe-Signature", header)
                .header("X-Forwarded-For", STRIPE_IP_ADDRESS)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH);
    }

    private Response notifyConnectorWithHeader(String payload, String stripeSignature, String forwardedIpAddresses) {
        return given()
                .port(app.getLocalPort())
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
        long chargeId = randomInt();

        String externalChargeId = "charge-" + chargeId;

        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withAmount(1000L)
                .withPaymentProvider("stripe")
                .withStatus(status)
                .withTransactionId(gatewayTransactionId)
                .withGatewayCredentialId(accountCredentialsParams.getId())
                .withIssuerUrl(ISSUER_URL)
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
