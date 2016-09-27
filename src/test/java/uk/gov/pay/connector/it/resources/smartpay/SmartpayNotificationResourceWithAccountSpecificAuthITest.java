package uk.gov.pay.connector.it.resources.smartpay;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.jayway.restassured.response.Response;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class SmartpayNotificationResourceWithAccountSpecificAuthITest extends CardResourceITestBase {

    private static final String NOTIFICATION_PATH = "/v1/api/notifications/smartpay";
    private static final String RESPONSE_EXPECTED_BY_SMARTPAY = "[accepted]";

    public SmartpayNotificationResourceWithAccountSpecificAuthITest() {
        super("smartpay");
    }

    @Before
    public void setUpAuthForEndpoint() {
        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "bobsbigsecret")))
                .post("/v1/api/accounts/" + accountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());
    }

    @Test
    public void shouldHandleASmartpayNotificationWithCorrectCredentials() throws Exception {
        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "bobsnewbigsecret")))
                .post("/v1/api/accounts/" + accountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());

        String transactionId = randomId();
        String chargeId = createNewChargeWith(CAPTURE_SUBMITTED, transactionId);

        String response = notifyConnectorWithCredentials(
                notificationPayloadForTransaction(transactionId, "notification-capture"),
                "bob", "bobsnewbigsecret")
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));

        assertFrontendChargeStatusIs(chargeId, "CAPTURED");
    }

    @Test
    public void shouldNotPermitASmartpayNotificationWithIncorrectCredentials() throws Exception {
        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "bobsnewbigsecret")))
                .post("/v1/api/accounts/" + accountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());

        String transactionId = randomId();

        notifyConnectorWithCredentials(
                notificationPayloadForTransaction(transactionId, "notification-capture"),
                "bob", "bobsnewwrongbigsecret")
                .then()
                .statusCode(401)
                .extract().body().asString();
    }


    @Test
    public void shouldIgnoreAuthorisedNotification() throws Exception {
        givenSetup()
                .body(toJson(ImmutableMap.of("username", "bob", "password", "bobsbigsecret")))
                .post("/v1/api/accounts/" + accountId + "/notification-credentials")
                .then()
                .statusCode(OK.getStatusCode());

        String transactionId = randomId();
        String chargeId = createNewChargeWith(CAPTURED, transactionId);

        String response = notifyConnector(notificationPayloadForTransaction(transactionId, "notification-authorisation"))
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));

        assertFrontendChargeStatusIs(chargeId, "CAPTURED");
    }

    @Test
    public void shouldHandleMultipleSmartpayNotifications() throws Exception {

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
    public void shouldKeepLatestSmartpayStatusFromNotifications() throws Exception {
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

    @Test
    public void shouldFailASmartpayNotificationWithAnUnexpectedContentType() throws Exception {
        String transactionId = randomId();
        createNewChargeWith(AUTHORISATION_SUCCESS, transactionId);

        given()
                .port(app.getLocalPort())
                .body(notificationPayloadForTransaction(transactionId, "notification-capture"))
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(415);
    }

    private Response notifyConnector(String payload) throws IOException {
        return given()
                .port(app.getLocalPort())
                .auth().basic("bob", "bobsbigsecret")
                .body(payload)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH);
    }

    private Response notifyConnectorWithCredentials(String payload, String username, String password) throws IOException {
        return given()
                .port(app.getLocalPort())
                .auth().basic(username, password)
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
