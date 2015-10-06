package uk.gov.pay.connector.it.resources;

import com.google.common.io.Resources;
import com.jayway.restassured.response.ResponseBodyExtractionOptions;
import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.WORLDPAY_PROVIDER;

public class NotificationResourceITest extends CardResourceITestBase {

    public static final String RESPONSE_EXPECTED_BY_WORLDPAY = "[OK]";
    private String NOTIFICATION_PATH = "/v1/api/notifications/worldpay";

    public NotificationResourceITest() {
        super(WORLDPAY_PROVIDER);
    }

    @Test
    public void shouldHandleAWorldpayNotification() throws Exception {

        String chargeId = sentAuthorisationRequestToWorldpay();

        assertFrontendChargeStatusIs(chargeId, "AUTHORISATION SUCCESS");

        String transactionId = readChargeTransactionId(chargeId);

        String worldpayNotification = readFromResources("worldpay/notification.xml")
                .replace("MyUniqueTransactionId!", transactionId);

        ResponseBodyExtractionOptions body = given().port(app.getLocalPort())
                .body(worldpayNotification)
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(200)
                .extract()
                .body();

        assertThat(body.asString(), is(RESPONSE_EXPECTED_BY_WORLDPAY));

//        assertFrontendChargeStatusIs(chargeId, "CAPTURED");
    }

    private String readChargeTransactionId(String chargeId) {
        return app.getDatabaseTestHelper().getChargeGatewayTransactionId(chargeId);
    }

    protected String sentAuthorisationRequestToWorldpay() {
        String chargeId = createNewCharge();

        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(buildJsonCardDetailsFor("4444333322221111"))
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(204);

        return chargeId;
    }

    protected void assertFrontendChargeStatusIs(String chargeId, String status) {
        assertStatusIs("/v1/frontend/charges/" + chargeId, status);
    }

    private void assertStatusIs(String url, String status) {
        given().port(app.getLocalPort())
                .get(url)
                .then()
                .body("status", is(status));
    }

    private String readFromResources(String filePath) throws IOException {
        return Resources.toString(getResource("templates/" + filePath), Charset.defaultCharset());
    }
}
