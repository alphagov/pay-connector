package uk.gov.pay.connector.it.resources.smartpay;

import com.google.common.collect.ImmutableMap;
import io.restassured.response.Response;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
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
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
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
public class SmartpayNotificationResourceWithAccountSpecificAuthITest extends ChargingITestBase {

    private static final String NOTIFICATION_PATH = "/v1/api/notifications/smartpay";
    private static final String RESPONSE_EXPECTED_BY_SMARTPAY = "[accepted]";

    public SmartpayNotificationResourceWithAccountSpecificAuthITest() {
        super("smartpay");
    }

    @Before
    public void setup() {
        super.setup();
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

        String reference = randomId();
        String transactionId = randomId();
        String externalChargeId = createNewChargeWith(CAPTURED, transactionId);
        Long chargeId = Long.parseLong(StringUtils.removeStart(externalChargeId, "charge-"));
        String externalRefundId = "refund-" + RandomUtils.nextInt();
        int refundId = databaseTestHelper.addRefund(externalRefundId, reference, 10L, REFUND_SUBMITTED, chargeId, randomAlphanumeric(10), ZonedDateTime.now());

        String response = notifyConnector(notificationPayloadForTransaction(externalRefundId, transactionId, reference, "notification-refund"))
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));
        assertFrontendChargeStatusIs(externalChargeId, "CAPTURED");
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

    private Response notifyConnector(String payload) {
        return given()
                .port(testContext.getPort())
                .auth().basic("bob", "bobsbigsecret")
                .body(payload)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH);
    }

    private Response notifyConnectorWithCredentials(String payload, String username, String password) {
        return given()
                .port(testContext.getPort())
                .auth().basic(username, password)
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
