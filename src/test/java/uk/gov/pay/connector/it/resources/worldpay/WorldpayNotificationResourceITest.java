package uk.gov.pay.connector.it.resources.worldpay;

import com.google.common.io.Resources;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.util.DnsUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static com.jayway.restassured.RestAssured.given;
import static java.lang.String.*;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class WorldpayNotificationResourceITest extends ChargingITestBase {

    private static final String RESPONSE_EXPECTED_BY_WORLDPAY = "[OK]";
    private static final String NOTIFICATION_PATH = "/v1/api/notifications/worldpay";

    public WorldpayNotificationResourceITest() {
        super("worldpay");
    }

    @Test
    public void shouldHandleAWorldpayNotification() throws Exception {
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
        String validIp = new DnsUtils().dnsLookup("build.ci.pymnt.uk").get();
        String xForwardedForHeader = format("%s, %s", validIp, "8.8.8.8");
        return given().port(app.getLocalPort())
                .body(notificationPayloadForTransaction(transactionId, status))
                .header("X-Forwarded-For", xForwardedForHeader)
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then();
    }

    private String notificationPayloadForTransaction(String transactionId, String status) throws IOException {
        URL resource = getResource("templates/worldpay/notification.xml");
        return Resources.toString(resource, Charset.defaultCharset())
                .replace("{{transactionId}}", transactionId)
                .replace("{{status}}", status)
                .replace("{{bookingDateDay}}", "10")
                .replace("{{bookingDateMonth}}", "01")
                .replace("{{bookingDateYear}}", "2017");
    }
}
