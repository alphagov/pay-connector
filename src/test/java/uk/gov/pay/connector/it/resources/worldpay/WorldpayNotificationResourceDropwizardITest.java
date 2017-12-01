package uk.gov.pay.connector.it.resources.worldpay;

import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.util.DnsUtils;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.io.IOException;

import static com.jayway.restassured.RestAssured.given;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_NOTIFICATION;

public class WorldpayNotificationResourceDropwizardITest extends ChargingITestBase {

    private static final String RESPONSE_EXPECTED_BY_WORLDPAY = "[OK]";
    private static final String NOTIFICATION_PATH = "/v1/api/notifications/worldpay";

    public WorldpayNotificationResourceDropwizardITest() {
        super("worldpay");
    }

    @Test
    public void shouldHandleAChargeNotification() throws Exception {
        String transactionId = "transaction-id";
        String chargeId = createNewChargeWith(CAPTURE_SUBMITTED, transactionId);

        String response = notifyConnector(transactionId, "CAPTURED")
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_WORLDPAY));

        assertFrontendChargeStatusIs(chargeId, CAPTURED.getValue());
    }

    @Test
    public void shouldHandleARefundNotification() throws Exception {
        String transactionId = "transaction-id";
        String refundExternalId = "12345";
        int refundAmount = 1000;

        String externalChargeId = createNewChargeWithRefund(transactionId, refundExternalId, refundAmount);

        String response = notifyConnector(transactionId, "REFUNDED", refundExternalId)
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_WORLDPAY));
        assertFrontendChargeStatusIs(externalChargeId, CAPTURED.getValue());
        assertRefundStatus(externalChargeId, refundExternalId, "success", refundAmount);
    }

    @Test
    public void shouldIgnoreAuthorisedNotification() throws Exception {

        String transactionId = "transaction-id";
        String chargeId = createNewChargeWith(CAPTURED, transactionId);

        String response = notifyConnector(transactionId, "AUTHORISED")
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_WORLDPAY));

        assertFrontendChargeStatusIs(chargeId, CAPTURED.getValue());
    }

    @Test
    public void shouldNotAddUnknownStatusToDatabaseFromANotification() throws Exception {
        String transactionId = "transaction-id";
        String chargeId = createNewChargeWith(CAPTURE_SUBMITTED, transactionId);

        String response = notifyConnector(transactionId, "GARBAGE")
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_WORLDPAY));

        assertFrontendChargeStatusIs(chargeId, CAPTURE_SUBMITTED.getValue());
    }

    @Test
    public void shouldNotUpdateStatusToDatabaseIfGatewayAccountIsNotFound() throws Exception {
        String chargeId = createNewCharge(AUTHORISATION_SUCCESS);

        notifyConnector("unknown-transation-id", "GARBAGE")
                .statusCode(200)
                .extract().body()
                .asString();

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldReturnForbiddenIfRequestComesFromUnexpectedIp() throws Exception {
        given().port(app.getLocalPort())
                .body(notificationPayloadForTransaction("any", "WHATEVER"))
                .header("X-Forwarded-For", "8.8.8.8, 123.1.23.32")
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(403);
    }

    @Test
    public void shouldReturnForbiddenIfXForwardedForHeaderIsMalformed() throws Exception {
        given().port(app.getLocalPort())
                .body(notificationPayloadForTransaction("any", "WHATEVER"))
                .header("X-Forwarded-For", "something is wrong, 8.8.8.8")
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(403);
    }

    @Test
    public void shouldReturnForbiddenIfXForwardedForHeaderIsMissing() throws Exception {
        given().port(app.getLocalPort())
                .body(notificationPayloadForTransaction("any", "WHATEVER"))
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(403);
    }

    @Test
    public void shouldFailWhenUnexpectedContentType() throws Exception {
        given().port(app.getLocalPort())
                .body(notificationPayloadForTransaction("any", "WHATEVER"))
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(415);
    }

    private ValidatableResponse notifyConnector(String transactionId, String status) throws Exception {
        return notifyConnector(notificationPayloadForTransaction(transactionId, status));
    }

    private ValidatableResponse notifyConnector(String transactionId, String status, String reference) throws Exception {
        return notifyConnector(notificationPayloadForTransaction(transactionId, status, reference));
    }

    private ValidatableResponse notifyConnector(String payload) throws Exception {
        String validIp = new DnsUtils().dnsLookup("build.ci.pymnt.uk").get();
        String xForwardedForHeader = format("%s, %s", validIp, "8.8.8.8");
        return given().port(app.getLocalPort())
                .body(payload)
                .header("X-Forwarded-For", xForwardedForHeader)
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then();
    }

    private String notificationPayloadForTransaction(String transactionId, String status) throws IOException {
        return TestTemplateResourceLoader.load(WORLDPAY_NOTIFICATION)
                .replace("{{transactionId}}", transactionId)
                .replace("{{status}}", status)
                .replace("{{bookingDateDay}}", "10")
                .replace("{{bookingDateMonth}}", "01")
                .replace("{{bookingDateYear}}", "2017");
    }

    private String notificationPayloadForTransaction(String transactionId, String status, String reference) throws IOException {
        String payload = notificationPayloadForTransaction(transactionId, status);
        return payload.replace("{{refund-ref}}", reference);
    }

    private String createNewChargeWithRefund(String transactionId, String refundExternalId, long refundAmount) {
        String externalChargeId = createNewChargeWith(CAPTURED, transactionId);
        String chargeId = externalChargeId.substring(externalChargeId.indexOf("-") + 1, externalChargeId.length());
        createNewRefund(REFUND_SUBMITTED, Long.valueOf(chargeId), refundExternalId, refundExternalId, refundAmount);
        return externalChargeId;
    }

}
