package uk.gov.pay.connector.it.resources.smartpay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import io.restassured.response.Response;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_MULTIPLE_NOTIFICATIONS;
import static uk.gov.pay.connector.util.TransactionId.randomId;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class SmartpayNotificationResourceWithAccountSpecificAuthIT extends ChargingITestBase {
    private static final String SMARTPAY_IP_ADDRESS = "1.1.1.1, 6.6.6.6";
    private static final String UNEXPECTED_IP_ADDRESS = "3.4.2.1";
    private static final String NOTIFICATION_PATH = "/v1/api/notifications/smartpay";
    private static final String RESPONSE_EXPECTED_BY_SMARTPAY = "[accepted]";

    public SmartpayNotificationResourceWithAccountSpecificAuthIT() {
        super("smartpay");
    }

    @Before
    public void setUp() {
        super.setUp();
        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "bobsbigsecret")))
                .post("/v1/api/accounts/" + accountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());
    }

    @Test
    public void shouldHandleASmartpayNotificationWithCorrectCredentials() {
        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "bobsnewbigsecret")))
                .post("/v1/api/accounts/" + accountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());

        String smartpayPaymentReference = randomId();
        String pspReference = randomId();
        String externalChargeId = createNewChargeWith(CAPTURE_SUBMITTED, smartpayPaymentReference);

        String response = notifyConnectorWithCredentials(
                notificationPayloadForTransaction(externalChargeId, smartpayPaymentReference, pspReference, "notification-capture"),
                "bob", "bobsnewbigsecret")
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));

        assertFrontendChargeStatusIs(externalChargeId, "CAPTURED");
        long chargeId = Long.parseLong(StringUtils.removeStart(externalChargeId, "charge-"));
        List<Map<String, Object>> chargeEvents = databaseTestHelper.getChargeEvents(chargeId);
        assertThat(chargeEvents, hasEvent(CAPTURED));
    }

    @Test
    public void shouldNotPermitASmartpayNotificationWithIncorrectCredentials() {
        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "bobsnewbigsecret")))
                .post("/v1/api/accounts/" + accountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());

        notifyConnectorWithCredentials(
                notificationPayloadForTransaction(randomId(), randomId(), randomId(), "notification-capture"),
                "bob", "bobsnewwrongbigsecret")
                .then()
                .statusCode(401)
                .extract().body().asString();
    }

    @Test
    public void shouldIgnoreAuthorisedNotification() {
        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "bobsbigsecret")))
                .post("/v1/api/accounts/" + accountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());

        String transactionId = randomId();
        String externalChargeId = createNewChargeWith(CAPTURED, transactionId);

        String response = notifyConnector(notificationPayloadForTransaction(randomId(), transactionId, randomId(), "notification-authorisation"))
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));

        assertFrontendChargeStatusIs(externalChargeId, "CAPTURED");
    }

    @Test
    public void shouldHandleRefundNotificationsCorrectly() {

        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "bobsbigsecret")))
                .post("/v1/api/accounts/" + accountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());

        String gatewayTransactionId = randomId();
        String transactionId = randomId();
        String externalChargeId = createNewChargeWith(CAPTURED, transactionId);
        String externalRefundId = "refund-" + RandomUtils.nextInt();
        int refundId = databaseTestHelper.addRefund(externalRefundId, 10L,
                REFUND_SUBMITTED, gatewayTransactionId, ZonedDateTime.now(),
                externalChargeId);

        String response = notifyConnector(notificationPayloadForTransaction(externalRefundId, transactionId, gatewayTransactionId, "notification-refund"))
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));
        assertFrontendChargeStatusIs(externalChargeId, "CAPTURED");
        assertRefundStatusIs(refundId, "REFUNDED");
    }

    @Test
    public void shouldHandleARefundNotification_forAnExpungedCharge() throws JsonProcessingException {
        String refundTransactionId = randomId();
        String transactionId = randomId();
        String externalChargeId = randomAlphanumeric(26);
        String externalRefundId = "refund-" + RandomUtils.nextInt();

        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "bobsbigsecret")))
                .post("/v1/api/accounts/" + accountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(getTestAccount())
                .withExternalChargeId(externalChargeId)
                .withTransactionId(transactionId);
        ledgerStub.returnLedgerTransactionForProviderAndGatewayTransactionId(testCharge, getPaymentProvider());

        int refundId = databaseTestHelper.addRefund(externalRefundId, 10L,
                REFUND_SUBMITTED, refundTransactionId, ZonedDateTime.now(),
                externalChargeId);

        String response = notifyConnector(notificationPayloadForTransaction(externalRefundId, transactionId, refundTransactionId, "notification-refund"))
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));

        Map<String, Object> chargeFromDB = databaseTestHelper.getChargeByExternalId(externalChargeId);
        assertThat(chargeFromDB, is(nullValue()));

        assertRefundStatusIs(refundId, "REFUNDED");
    }

    @Test
    public void shouldHandleMultipleSmartpayNotifications() {

        String transactionId = randomId();
        String transactionId2 = randomId();
        String chargeId = createNewChargeWith(CAPTURE_SUBMITTED, transactionId);
        String chargeId2 = createNewChargeWith(CREATED, transactionId2);

        String response = notifyConnector(multipleNotifications(transactionId, transactionId2))
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));

        assertFrontendChargeStatusIs(chargeId, CAPTURED.getValue());
        assertFrontendChargeStatusIs(chargeId2, CREATED.getValue());
    }

    @Test
    public void shouldKeepLatestSmartpayStatusFromNotifications() {
        String transactionId = randomId();
        String chargeId = createNewChargeWith(CAPTURE_SUBMITTED, transactionId);

        String response = notifyConnector(multipleNotifications(transactionId, transactionId))
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));

        assertFrontendChargeStatusIs(chargeId, CAPTURED.getValue());
    }

    @Test
    public void shouldIgnoreASmartpayNotificationWithoutAuth() {
        String transactionId = randomId();
        createNewChargeWith(AUTHORISATION_SUCCESS, transactionId);

        given()
                .port(testContext.getPort())
                .body(notificationPayloadForTransaction(randomId(), transactionId, randomId(), "notification-capture"))
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(401);
    }

    @Test
    public void shouldFailASmartpayNotificationWithAnUnexpectedContentType() {
        String transactionId = randomId();
        createNewChargeWith(AUTHORISATION_SUCCESS, transactionId);

        given()
                .port(testContext.getPort())
                .body(notificationPayloadForTransaction(randomId(), transactionId, randomId(), "notification-capture"))
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(415);
    }

    @Test
    public void shouldReturnForbiddenIfRequestComesFromUnexpectedIpAddress() {
        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "bobsnewbigsecret")))
                .post("/v1/api/accounts/" + accountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());
        String smartpayPaymentReference = randomId();
        String externalChargeId = createNewChargeWith(CAPTURE_SUBMITTED, smartpayPaymentReference);

        notifyConnectorWithCredentials(notificationPayloadForTransaction(externalChargeId, smartpayPaymentReference, randomId(), "notification-capture"),
                "bob",
                "bobsnewbigsecret",
                UNEXPECTED_IP_ADDRESS)
                .then()
                .statusCode(403);
    }

    private Response notifyConnector(String payload) {
        return given()
                .port(testContext.getPort())
                .auth().basic("bob", "bobsbigsecret")
                .header("X-Forwarded-For", SMARTPAY_IP_ADDRESS)
                .body(payload)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH);
    }

    private Response notifyConnectorWithCredentials(String payload, String username, String password) {
        return notifyConnectorWithCredentials(payload, username, password, SMARTPAY_IP_ADDRESS);
    }

    private Response notifyConnectorWithCredentials(String payload, String username, String password, String forwardedIpAddresses) {
        return given()
                .port(testContext.getPort())
                .auth().basic(username, password)
                .header("X-Forwarded-For", forwardedIpAddresses)
                .body(payload)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH);
    }

    private String notificationPayloadForTransaction(String merchantReference, String originalReference, String pspReference, String fileName) {
        return fixture("templates/smartpay/" + fileName + ".json")
                .replace("{{merchantReference}}", merchantReference)
                .replace("{{originalReference}}", originalReference)
                .replace("{{pspReference}}", pspReference);
    }

    private String multipleNotifications(String transactionId, String transactionId2) {
        return TestTemplateResourceLoader.load(SMARTPAY_MULTIPLE_NOTIFICATIONS)
                .replace("{{pspReference1}}", transactionId)
                .replace("{{pspReference2}}", transactionId2);
    }

    private void assertRefundStatusIs(int refundId, String expectedStatus) {
        List<Map<String, Object>> refund = databaseTestHelper.getRefund(refundId);
        assertThat(refund.get(0).get("status"), is(expectedStatus));
    }

}
