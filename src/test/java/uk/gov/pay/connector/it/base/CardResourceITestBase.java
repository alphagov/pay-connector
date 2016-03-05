package uk.gov.pay.connector.it.base;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
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
import uk.gov.pay.connector.util.RestAssuredClient;

import java.io.IOException;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static io.dropwizard.testing.ConfigOverride.config;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.GatewayAccount.*;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class CardResourceITestBase {
    private RestAssuredClient connectorRestApi;

    @Rule
    public DropwizardAppWithPostgresRule app;

    private int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    protected WorldpayMockClient worldpay;

    protected SmartpayMockClient smartpay;

    protected final String accountId;
    private final String paymentProvider;
    private long amount = 6234;

    public CardResourceITestBase(String paymentProvider) {
        this.paymentProvider = paymentProvider;
        this.accountId = String.valueOf(RandomUtils.nextInt(99999));

        app = new DropwizardAppWithPostgresRule(
                config("worldpay.url", "http://localhost:" + port + "/jsp/merchant/xml/paymentService.jsp"),
                config("smartpay.url", "http://localhost:" + port + "/pal/servlet/soap/Payment"));
        connectorRestApi = new RestAssuredClient(app, accountId);
    }

    @Before
    public void setup() throws IOException {
        worldpay = new WorldpayMockClient();
        smartpay = new SmartpayMockClient();

        Map<String, String> credentials = ImmutableMap.of(
                CREDENTIALS_MERCHANT_ID, "merchant-id",
                CREDENTIALS_USERNAME, "test-user",
                CREDENTIALS_PASSWORD, "test-password"
        );
        app.getDatabaseTestHelper().addGatewayAccount(accountId, paymentProvider, credentials);
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
        connectorRestApi
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status));
    }

    protected void assertApiStatusIs(String chargeId, String status) {
        connectorRestApi
                .withChargeId(chargeId)
                .getCharge()
                .body("status", is(status));
    }

    protected String authoriseNewCharge() {
        return createNewChargeWith(AUTHORISATION_SUCCESS, "");
    }


    protected String createNewCharge() {
        return createNewChargeWith(CREATED, "");
    }

    protected String createNewChargeWith(ChargeStatus status, String gatewayTransactionId) {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();

        app.getDatabaseTestHelper().addCharge(chargeId, accountId, amount, status, "returnUrl", gatewayTransactionId);
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

    protected void shouldReturnErrorForCardDetailsWithMessage(String cardDetails, String errorMessage, String status) throws Exception {

        String chargeId = createNewChargeWith(ENTERING_CARD_DETAILS, null);

        givenSetup()
                .body(cardDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(errorMessage));

        assertFrontendChargeStatusIs(chargeId, status);
    }

    protected String authoriseChargeUrlFor(String chargeId) {
        return FRONTEND_AUTHORIZATION_RESOURCE.replace("{chargeId}", chargeId);
    }

    protected String captureChargeUrlFor(String chargeId) {
        return FRONTEND_CAPTURE_RESOURCE.replace("{chargeId}", chargeId);
    }

    protected String cancelChargeUrlFor(String accountId, String chargeId) {
        return CANCEL_CHARGE_PATH.replace("{accountId}", accountId).replace("{chargeId}", chargeId);
    }
}
