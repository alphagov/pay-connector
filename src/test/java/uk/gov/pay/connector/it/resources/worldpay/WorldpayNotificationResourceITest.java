package uk.gov.pay.connector.it.resources.worldpay;

import com.google.common.io.Resources;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.WORLDPAY_PROVIDER;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class WorldpayNotificationResourceITest extends CardResourceITestBase {

    private static final String RESPONSE_EXPECTED_BY_WORLDPAY = "[OK]";
    private static final String NOTIFICATION_PATH = "/v1/api/notifications/worldpay";

    public WorldpayNotificationResourceITest() {
        super(WORLDPAY_PROVIDER);
    }

    @Test
    public void shouldHandleAWorldpayNotification() throws Exception {

        String transactionId = randomId();
        String chargeId = createNewChargeWith(AUTHORISATION_SUCCESS, transactionId);

        worldpay.mockInquiryResponse(transactionId, "REFUSED");

        String response = notifyConnector(transactionId)
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_WORLDPAY));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_REJECTED.getValue());
    }

    @Test
    public void shouldNotAddUnknownStatusToDatabase() throws Exception {
        String transactionId = randomId();
        String chargeId = createNewChargeWith(AUTHORISATION_SUCCESS, transactionId);

        worldpay.mockInquiryResponse(transactionId, "PAID IN FULL WITH CABBAGES");

        String response = notifyConnector(transactionId)
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_WORLDPAY));

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    @Test
    public void shouldReturnErrorIfInquiryForChargeStatusFails() throws Exception {
        String transactionId = randomId();
        String chargeId = createNewChargeWith(AUTHORISATION_SUCCESS, transactionId);

        worldpay.mockErrorResponse();

        notifyConnector(transactionId)
                .statusCode(502);

        assertFrontendChargeStatusIs(chargeId, AUTHORISATION_SUCCESS.getValue());
    }

    private ValidatableResponse notifyConnector(String transactionId) throws IOException {
        return given().port(app.getLocalPort())
                .body(notificationPayloadForTransaction(transactionId))
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then();
    }

    private String notificationPayloadForTransaction(String transactionId) throws IOException {
        URL resource = getResource("templates/worldpay/notification.xml");
        return Resources.toString(resource, Charset.defaultCharset()).replace("{{transactionId}}", transactionId);
    }
}
