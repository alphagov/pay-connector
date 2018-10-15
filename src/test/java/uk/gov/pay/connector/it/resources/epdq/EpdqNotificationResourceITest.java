package uk.gov.pay.connector.it.resources.epdq;

import com.google.common.collect.Lists;
import com.jayway.restassured.response.ValidatableResponse;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.epdq.EpdqSha512SignatureGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_SUBMITTED;

@RunWith(JUnitParamsRunner.class)
public class EpdqNotificationResourceITest extends ChargingITestBase {

    private static final String RESPONSE_EXPECTED_BY_EPDQ = "[OK]";
    private static final String NOTIFICATION_PATH = "/v1/api/notifications/epdq";

    public EpdqNotificationResourceITest() {
        super("epdq");
    }

    @Test
    @Parameters({
            "CAPTURE_READY",
            "CAPTURE_SUBMITTED",
            "CAPTURE_APPROVED_RETRY"
    })
    public void shouldHandleACaptureNotification(ChargeStatus chargeStatus) throws Exception {
        String transactionId = "transaction-id";
        String chargeId = createNewChargeWith(chargeStatus, transactionId);

        String response = notifyConnector(transactionId, "1", "9", getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE))
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_EPDQ));

        assertFrontendChargeStatusIs(chargeId, CAPTURED.getValue());
    }

    @Test
    public void shouldHandleARefundNotification() throws Exception {
        String transactionId = "123456";
        String payIdSub = "2";
        String refundExternalId = "999999";
        int refundAmount = 1000;

        String externalChargeId = createNewChargeWithRefund(transactionId, refundExternalId, payIdSub, refundAmount);

        String response = notifyConnector(transactionId, payIdSub, "8", getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE))
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_EPDQ));

        assertFrontendChargeStatusIs(externalChargeId, CAPTURED.getValue());
        assertRefundStatus(externalChargeId, refundExternalId, "success", refundAmount);
    }

    private String createNewChargeWithRefund(String transactionId, String refundExternalId, String payIdSub, long refundAmount) {
        String externalChargeId = createNewChargeWith(CAPTURED, transactionId);
        String chargeId = externalChargeId.substring(externalChargeId.indexOf("-") + 1, externalChargeId.length());
        createNewRefund(REFUND_SUBMITTED, Long.valueOf(chargeId), refundExternalId, transactionId + "/" + payIdSub, refundAmount);
        return externalChargeId;
    }

    @Test
    public void shouldNotAddUnknownStatusToDatabaseFromANotification() throws Exception {
        String transactionId = "transaction-id";
        String chargeId = createNewChargeWith(CAPTURE_SUBMITTED, transactionId);

        String response = notifyConnector(transactionId, "GARBAGE", getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE))
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_EPDQ));

        assertFrontendChargeStatusIs(chargeId, CAPTURE_SUBMITTED.getValue());
    }

    @Test
    public void shouldNotUpdateStatusToDatabaseIfGatewayAccountIsNotFound() throws Exception {
        String chargeId = createNewCharge(AUTHORISATION_SUCCESS);

        notifyConnector("unknown-transation-id", "GARBAGE", getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE))
                .statusCode(200)
                .extract().body()
                .asString();

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldNotUpdateStatusToDatabaseIfShaSignatureIsIncorrect() throws Exception {
        String transactionId = "transaction-id";
        String chargeId = createNewChargeWith(CAPTURE_SUBMITTED, transactionId);

        String response = notifyConnector(transactionId, "9", "Incorrect-sha-out-passphrase")
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_EPDQ));

        assertFrontendChargeStatusIs(chargeId, CAPTURE_SUBMITTED.getValue());
    }

    @Test
    public void shouldFailWhenUnexpectedContentType() throws Exception {
        given().port(app.getLocalPort())
                .body(notificationPayloadForTransaction("any", "1", "WHATEVER", getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE)))
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(415);
    }

    private ValidatableResponse notifyConnector(String transactionId, String status, String shaOutPassphrase) throws Exception {
        return notifyConnector(notificationPayloadForTransaction(transactionId, status, shaOutPassphrase));
    }

    private ValidatableResponse notifyConnector(String transactionId, String payIdSub, String status, String shaOutPassphrase) throws Exception {
        return notifyConnector(notificationPayloadForTransaction(transactionId, payIdSub, status, shaOutPassphrase));
    }

    private ValidatableResponse notifyConnector(String payload) throws Exception {
        return given().port(app.getLocalPort())
                .body(payload)
                .contentType(APPLICATION_FORM_URLENCODED)
                .post(NOTIFICATION_PATH)
                .then();
    }

    private String notificationPayloadForTransaction(String transactionId, String status, String shaOutPassphrase) throws IOException {
        List<NameValuePair> payloadParameters = buildPayload(transactionId, status);
        return notificationPayloadForTransaction(payloadParameters, shaOutPassphrase);
    }

    private String notificationPayloadForTransaction(String transactionId, String payIdSub, String status, String shaOutPassphrase) throws IOException {
        List<NameValuePair> payloadParameters = buildPayload(transactionId, status);
        payloadParameters.add(new BasicNameValuePair("PAYIDSUB", payIdSub));
        return notificationPayloadForTransaction(payloadParameters, shaOutPassphrase);
    }

    private List<NameValuePair> buildPayload(String transactionId, String status) {
        return Lists.newArrayList(
                new BasicNameValuePair("orderID", "order-id"),
                new BasicNameValuePair("STATUS", status),
                new BasicNameValuePair("PAYID", transactionId));
    }

    private String notificationPayloadForTransaction(List<NameValuePair> payloadParameters, String shaOutPassphrase) {
        String signature = new EpdqSha512SignatureGenerator().sign(payloadParameters, shaOutPassphrase);

        payloadParameters.add(new BasicNameValuePair("SHASIGN", signature));
        return URLEncodedUtils.format(payloadParameters, StandardCharsets.UTF_8.toString());
    }
}
