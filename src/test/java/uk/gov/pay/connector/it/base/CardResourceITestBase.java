package uk.gov.pay.connector.it.base;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonObject;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.rules.SmartpayMockClient;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.PortFactory;

import java.io.IOException;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static io.dropwizard.testing.ConfigOverride.config;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.CardResource.*;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class CardResourceITestBase {

    @Rule
    public DropwizardAppWithPostgresRule app;

    private int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    protected WorldpayMockClient worldpay;

    protected SmartpayMockClient smartpay;

    protected final String accountId;
    private final String paymentProvider;

    public CardResourceITestBase(String paymentProvider) {
        this.paymentProvider = paymentProvider;
        this.accountId = String.valueOf(RandomUtils.nextInt(99999));

        app = new DropwizardAppWithPostgresRule(
                config("worldpay.url", "http://localhost:" + port + "/jsp/merchant/xml/paymentService.jsp"),
                config("smartpay.url", "http://localhost:" + port + "/pal/servlet/soap/Payment"));
    }

    @Before
    public void setup() throws IOException {
        worldpay = new WorldpayMockClient();
        smartpay = new SmartpayMockClient();

        app.getDatabaseTestHelper().addGatewayAccount(accountId, paymentProvider);
    }

    protected String cardDetailsWithMinimalAddress(String cardNumber) {
        JsonObject addressObject = new JsonObject();

        addressObject.addProperty("line1", "The Money Pool");
        addressObject.addProperty("city", "London");
        addressObject.addProperty("postcode", "DO11 4RS");
        addressObject.addProperty("country", "GB");

        JsonObject cardDetails = new JsonObject();
        cardDetails.addProperty("card_number", cardNumber);
        cardDetails.addProperty("cvc", "123");
        cardDetails.addProperty("expiry_date", "12/21");
        cardDetails.addProperty("cardholder_name", "Mr. Payment");
        cardDetails.add("address", addressObject);
        return toJson(cardDetails);
    }

    protected String buildJsonCardDetailsFor(String cardNumber) {
        return buildJsonCardDetailsFor(cardNumber, "123", "11/99");
    }

    protected String buildJsonCardDetailsFor(String cardHolderName, String cardNumber) {
        return buildJsonCardDetailsFor(cardHolderName, cardNumber, "123", "11/99", null, "London", null);
    }

    protected String buildJsonCardDetailsFor(String cardNumber, String cvc, String expiryDate) {
        return buildJsonCardDetailsFor("Mr. Payment", cardNumber, cvc, expiryDate, null, "London", null);
    }

    protected void assertFrontendChargeStatusIs(String chargeId, String status) {
        assertStatusIs("/v1/frontend/charges/" + chargeId, status);
    }

    protected void assertApiStatusIs(String chargeId, String status) {
        assertStatusIs("/v1/api/charges/" + chargeId, status);
    }

    private void assertStatusIs(String url, String status) {
        given().port(app.getLocalPort())
                .get(url)
                .then()
                .body("status", is(status));
    }

    protected String authoriseNewCharge() {
        return createNewChargeWith(AUTHORISATION_SUCCESS, null);
    }


    protected String createNewCharge() {
        return createNewChargeWith(CREATED, null);
    }

    protected String createNewChargeWith(ChargeStatus status, String gatewayTransactionId) {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();

        app.getDatabaseTestHelper().addCharge(chargeId, accountId, 500, status, "returnUrl", gatewayTransactionId);
        return chargeId;
    }

    protected RequestSpecification givenSetup() {
        return given().port(app.getLocalPort())
                .contentType(JSON);
    }

    protected String buildJsonCardDetailsWithFullAddress() {
        return buildJsonCardDetailsFor(
                "Scrooge McDuck",
                "4242424242424242",
                "123",
                "11/99",
                "Moneybags Avenue",
                "London",
                "Greater London"
        );
    }

    protected String buildJsonCardDetailsFor(String cardHolderName, String cardNumber, String cvc, String expiryDate, String line2, String city, String county) {
        JsonObject addressObject = new JsonObject();

        addressObject.addProperty("line1", "The Money Pool");
        addressObject.addProperty("line2", line2);
        addressObject.addProperty("city", city);
        addressObject.addProperty("county", county);
        addressObject.addProperty("postcode", "DO11 4RS");
        addressObject.addProperty("country", "GB");

        JsonObject cardDetails = new JsonObject();
        cardDetails.addProperty("card_number", cardNumber);
        cardDetails.addProperty("cvc", cvc);
        cardDetails.addProperty("expiry_date", expiryDate);
        cardDetails.addProperty("cardholder_name", cardHolderName);
        cardDetails.add("address", addressObject);
        return toJson(cardDetails);
    }

    protected String accountBodyFor(String accountId) {
        JsonObject body = new JsonObject();
        body.addProperty("account_id", accountId);
        return toJson(body);
    }

    protected void shouldReturnErrorForCardDetailsWithMessage(String cardDetails, String errorMessage, String status) throws Exception {
        String chargeId = createNewChargeWith(ENTERING_CARD_DETAILS, null);

        givenSetup()
                .body(cardDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is(errorMessage));

        assertFrontendChargeStatusIs(chargeId, status);
    }

    protected String authoriseChargeUrlFor(String chargeId) {
        return AUTHORIZATION_FRONTEND_RESOURCE_PATH.replace("{chargeId}", chargeId);
    }

    protected String captureChargeUrlFor(String chargeId) {
        return CAPTURE_FRONTEND_RESOURCE_PATH.replace("{chargeId}", chargeId);
    }

    protected String cancelChargeUrlFor(String chargeId) {
        return CANCEL_CHARGE_PATH.replace("{chargeId}", chargeId);
    }
}
