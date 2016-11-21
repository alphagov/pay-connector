package uk.gov.pay.connector.it.base;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import uk.gov.pay.connector.model.domain.CardFixture;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.rules.SmartpayMockClient;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.PortFactory;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static io.dropwizard.testing.ConfigOverride.config;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.GatewayAccount.*;
import static uk.gov.pay.connector.resources.ApiPaths.*;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class CardResourceITestBase {
    private RestAssuredClient connectorRestApi;

    private int port = PortFactory.findFreePort();

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
                config("worldpay.urls.test", "http://localhost:" + port + "/jsp/merchant/xml/paymentService.jsp"),
                config("smartpay.urls.test", "http://localhost:" + port + "/pal/servlet/soap/Payment"));

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);
    protected static final long AMOUNT = 6234L;

    protected WorldpayMockClient worldpay;

    protected SmartpayMockClient smartpay;

    protected final String accountId;
    private final String paymentProvider;

    public CardResourceITestBase(String paymentProvider) {
        this.paymentProvider = paymentProvider;
        this.accountId = String.valueOf(RandomUtils.nextInt());

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

    protected String cardDetailsWithMinimalAddress(String cardNumber, String cardBrand) {
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
        cardDetails.addProperty("card_brand", cardBrand);
        cardDetails.add("address", addressObject);
        return toJson(cardDetails);
    }

    protected String buildJsonCardDetailsFor(String cardNumber, String cardBrand) {
        return buildJsonCardDetailsFor(cardNumber, "123", "11/99", cardBrand);
    }

    protected String buildJsonCardDetailsFor(String cardHolderName, String cardNumber, String cardBrand) {
        return buildJsonCardDetailsFor(cardHolderName, cardNumber, "123", "11/99", cardBrand, "The Money Pool", null, "London", null, "DO11 4RS", "GB");
    }

    protected String buildJsonCardDetailsFor(String cardNumber, String cvc, String expiryDate, String cardBrand) {
        return buildJsonCardDetailsFor("Mr. Payment", cardNumber, cvc, expiryDate, cardBrand, "The Money Pool", null, "London", null, "DO11 4RS", "GB");
    }

    protected void assertFrontendChargeStatusIs(String chargeId, String status) {
        connectorRestApi
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status));
    }

    protected void assertApiStateIs(String chargeId, String stateString) {
        connectorRestApi
                .withChargeId(chargeId)
                .getCharge()
                .body("state.status", is(stateString));
    }

    protected String authoriseNewCharge() {
        String externalChargeId = createNewChargeWith(AUTHORISATION_SUCCESS, "");
        app.getDatabaseTestHelper().updateChargeCardDetails(
                Long.parseLong(externalChargeId.replace("charge-","")),
                CardFixture.aValidCard().withCardNo("1234").build());
        return externalChargeId;
    }


    protected String createNewCharge() {
        return createNewChargeWith(CREATED, "");
    }

    protected String createNewRefundWith(RefundStatus refundStatus, Long amount,Long chargeId, String transactionId) {
        long refundId = RandomUtils.nextInt();
        String externalRefundId = "refund-" + refundId;
        app.getDatabaseTestHelper().addRefund(refundId,externalRefundId,transactionId, amount,refundStatus.getValue(),chargeId, ZonedDateTime.now());
        return externalRefundId;
    }

    protected String createNewChargeWith(ChargeStatus status, String gatewayTransactionId) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, 6234L, status, "returnUrl", gatewayTransactionId);
        return externalChargeId;
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
                "cardBrand",
                "The Money Pool",
                "Moneybags Avenue",
                "London",
                "Greater London",
                "DO11 4RS",
                "GB"
        );
    }

    protected String buildJsonCardDetailsFor(String cardHolderName, String cardNumber, String cvc, String expiryDate, String cardBrand, String line1, String line2, String city, String county, String postCode, String countryCode) {
        JsonObject addressObject = new JsonObject();

        addressObject.addProperty("line1", line1);
        addressObject.addProperty("line2", line2);
        addressObject.addProperty("city", city);
        addressObject.addProperty("county", county);
        addressObject.addProperty("postcode", postCode);
        addressObject.addProperty("country", countryCode);

        JsonObject cardDetails = new JsonObject();
        cardDetails.addProperty("card_number", cardNumber);
        cardDetails.addProperty("cvc", cvc);
        cardDetails.addProperty("expiry_date", expiryDate);
        cardDetails.addProperty("card_brand", cardBrand);
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
        return FRONTEND_CHARGE_AUTHORIZE_API_PATH.replace("{chargeId}", chargeId);
    }

    protected String captureChargeUrlFor(String chargeId) {
        return FRONTEND_CHARGE_CAPTURE_API_PATH.replace("{chargeId}", chargeId);
    }

    protected String cancelChargeUrlFor(String accountId, String chargeId) {
        return CHARGE_CANCEL_API_PATH.replace("{accountId}", accountId).replace("{chargeId}", chargeId);
    }

    protected String addCharge(ChargeStatus status, String reference, ZonedDateTime fromDate, String gatewayTransactionId) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, "http://somereturn.gov.uk", gatewayTransactionId, reference, fromDate);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());
        app.getDatabaseTestHelper().updateChargeCardDetails(
                chargeId,
                CardFixture.aValidCard().withCardNo("1234").build());
        return externalChargeId;
    }

    protected Matcher<? super List<Map<String, Object>>> hasEventWithStatusAndTransactionId(ChargeStatus chargeStatus, String transactionId) {
        return new TypeSafeMatcher<List<Map<String, Object>>>() {
            @Override
            protected boolean matchesSafely(List<Map<String, Object>> chargeEvents) {
                return chargeEvents.stream()
                        .anyMatch(chargeEvent ->
                                chargeStatus.getValue().equals(chargeEvent.get("status")) && transactionId.equals(chargeEvent.get("gateway_transaction_id"))
                        );
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(format("no matching charge event with status [%s] with transactionId [%s]", chargeStatus.getValue(), transactionId));
            }
        };
    }
}
