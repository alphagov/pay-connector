package uk.gov.pay.connector.it.resources.smartpay;

import com.google.common.io.Resources;
import com.jayway.restassured.response.Response;
import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.UUID;

import static com.google.common.io.Resources.getResource;
import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.SMARTPAY_PROVIDER;

public class SmartpayNotificationResourceITest extends CardResourceITestBase {

    private static final String NOTIFICATION_PATH = "/v1/api/notifications/smartpay";
    private static final String RESPONSE_EXPECTED_BY_SMARTPAY = "[accepted]";

    public SmartpayNotificationResourceITest() {
        super(SMARTPAY_PROVIDER);
    }


    @Test
    public void shouldHandleASmartpayNotification() throws Exception {

        String transactionId = UUID.randomUUID().toString();
        String chargeId = createNewChargeWith(AUTHORISATION_SUCCESS, transactionId);

        String response = notifyCaptureToConnector(transactionId)
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_SMARTPAY));

//        assertFrontendChargeStatusIs(chargeId, "CAPTURED");
    }

    private Response notifyCaptureToConnector(String transactionId) throws IOException {
        return given()
                .port(app.getLocalPort())
                .body(notificationPayloadForTransaction(transactionId))
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH);
    }

    private String notificationPayloadForTransaction(String transactionId) throws IOException {
        URL resource = getResource("templates/smartpay/notification-capture.json");
        return Resources.toString(resource, Charset.defaultCharset())
                .replace("{{transactionId}}", transactionId);
    }
}
