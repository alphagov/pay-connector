package uk.gov.pay.connector.it.resources.smartpay;

import com.google.common.io.Resources;
import com.jayway.restassured.response.Response;
import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.SMARTPAY_PROVIDER;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class SmartpayNotificationResourceITest extends CardResourceITestBase {

    private static final String NOTIFICATION_PATH = "/v1/api/notifications/smartpay";
    private static final String RESPONSE_EXPECTED_BY_SMARTPAY = "[accepted]";

    public SmartpayNotificationResourceITest() {
        super(SMARTPAY_PROVIDER);
    }

    @Test
    public void shouldHandleASmartpayNotification() throws Exception {

        String transactionId = randomId();
        String chargeId = createNewChargeWith(AUTHORISATION_SUCCESS, transactionId);

        String response = notifyConnector(notificationPayloadForTransaction(transactionId, "notification-capture"))
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));

        assertFrontendChargeStatusIs(chargeId, "CAPTURED");
    }
    @Test
    public void shouldHandleANotificationForFailedEvent() throws Exception {

        String transactionId = randomId();
        String chargeId = createNewChargeWith(AUTHORISATION_SUBMITTED, transactionId);

        String response = notifyConnector(notificationPayloadForTransaction(transactionId, "notification-authorisation"))
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));

        assertFrontendChargeStatusIs(chargeId, "AUTHORISATION REJECTED");
    }

    @Test
    public void shouldHandleMultipleSmartpayNotifications() throws Exception {

        String transactionId = randomId();
        String transactionId2 = randomId();
        String chargeId = createNewChargeWith(AUTHORISATION_SUCCESS, transactionId);
        String chargeId2 = createNewChargeWith(CREATED, transactionId2);

        String response = notifyConnector(multipleNotifications(transactionId, transactionId2))
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));

        assertFrontendChargeStatusIs(chargeId, "CAPTURED");
        assertFrontendChargeStatusIs(chargeId2, "AUTHORISATION SUCCESS");
    }

    @Test
    public void shouldKeepLatestSmartpayStatusFromNotifications() throws Exception {
        String transactionId = randomId();
        String chargeId = createNewChargeWith(CREATED, transactionId);

        String response = notifyConnector(multipleNotifications(transactionId, transactionId))
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));

        assertFrontendChargeStatusIs(chargeId, "CAPTURED");
    }

    @Test
    public void shouldIgnoreASmartpayNotificationWithoutAuth() throws Exception {
        String transactionId = randomId();
        createNewChargeWith(AUTHORISATION_SUCCESS, transactionId);

        given()
                .port(app.getLocalPort())
                .body(notificationPayloadForTransaction(transactionId, "notification-capture"))
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(401);
    }


    private Response notifyConnector(String payload) throws IOException {
        return given()
                .port(app.getLocalPort())
                .auth().basic("smartpay_test_username", "smartpay_test_password")
                .body(payload)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH);
    }

    private String notificationPayloadForTransaction(String transactionId, String fileName) throws IOException {
        URL resource = getResource("templates/smartpay/"+fileName+".json");
        return Resources.toString(resource, Charset.defaultCharset())
                .replace("{{transactionId}}", transactionId);
    }

    private String multipleNotifications(String transactionId, String transactionId2) throws IOException {
        URL resource = getResource("templates/smartpay/multiple-notifications.json");
        return Resources.toString(resource, Charset.defaultCharset())
                .replace("{{transactionId}}", transactionId)
                .replace("{{transactionId2}}", transactionId2);
    }
}
