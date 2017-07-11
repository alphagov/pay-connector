package uk.gov.pay.connector.it.resources.epdq;

import com.jayway.restassured.response.ValidatableResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.service.epdq.EpdqSha512SignatureGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;

public class EpdqNotificationResourceITest extends ChargingITestBase {

    private static final String RESPONSE_EXPECTED_BY_EPDQ = "[OK]";
    private static final String NOTIFICATION_PATH = "/v1/api/notifications/epdq";

    public EpdqNotificationResourceITest() {
        super("epdq");
    }

    @Test
    public void shouldHandleANotification() throws Exception {
        String transactionId = "transaction-id";
        String chargeId = createNewChargeWith(CAPTURE_SUBMITTED, transactionId);

        String response = notifyConnector(transactionId, "9", getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE))
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_EPDQ));

        assertFrontendChargeStatusIs(chargeId, CAPTURED.getValue());
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
                .body(notificationPayloadForTransaction("any", "WHATEVER", getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE)))
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(415);
    }

    private ValidatableResponse notifyConnector(String transactionId, String status, String shaOutPassphrase) throws Exception {
        return given().port(app.getLocalPort())
                .body(notificationPayloadForTransaction(transactionId, status, shaOutPassphrase))
                .contentType(APPLICATION_FORM_URLENCODED)
                .post(NOTIFICATION_PATH)
                .then();
    }

    private String notificationPayloadForTransaction(String transactionId, String status, String shaOutPassphrase) throws IOException {
        List<NameValuePair> payloadParameters = new ArrayList<>(Arrays.asList(
                new BasicNameValuePair("orderID", "order-id"),
                new BasicNameValuePair("STATUS", status),
                new BasicNameValuePair("PAYID", transactionId)));

        String signature = new EpdqSha512SignatureGenerator().sign(payloadParameters, shaOutPassphrase);

        payloadParameters.add(new BasicNameValuePair("SHASIGN", signature));
        return URLEncodedUtils.format(payloadParameters, StandardCharsets.UTF_8.toString());
    }
}
