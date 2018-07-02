package uk.gov.pay.connector.it.base;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import uk.gov.pay.connector.model.ServicePaymentReference;
import uk.gov.pay.connector.model.domain.CardFixture;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.rules.SmartpayMockClient;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.TransactionId.randomId;

abstract public class ChargingITestCommon {
    private RestAssuredClient connectorRestApi;

    protected static final long AMOUNT = 6234L;

    protected WorldpayMockClient worldpay;
    protected SmartpayMockClient smartpay;

    protected final String accountId;
    private final String paymentProvider;

    public ChargingITestCommon(String paymentProvider) {
        this.paymentProvider = paymentProvider;
        this.accountId = String.valueOf(RandomUtils.nextInt());
    }

    abstract public DropwizardAppWithPostgresRule getApplication();

    public RestAssuredClient getConnectorRestApi() {
        if (connectorRestApi == null) {
            connectorRestApi = new RestAssuredClient(getApplication(), accountId);
        }
        return connectorRestApi;
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
        getApplication().getDatabaseTestHelper().addGatewayAccount(accountId, paymentProvider, credentials);
    }

    protected String authorisationDetailsWithMinimalAddress(String cardNumber, String cardBrand) {
        JsonObject addressObject = new JsonObject();

        addressObject.addProperty("line1", "The Money Pool");
        addressObject.addProperty("city", "London");
        addressObject.addProperty("postcode", "DO11 4RS");
        addressObject.addProperty("country", "GB");

        JsonObject authorisationDetails = new JsonObject();
        authorisationDetails.addProperty("card_number", cardNumber);
        authorisationDetails.addProperty("cvc", "123");
        authorisationDetails.addProperty("expiry_date", "12/21");
        authorisationDetails.addProperty("cardholder_name", "Mr. Payment");
        authorisationDetails.addProperty("card_brand", cardBrand);
        authorisationDetails.add("address", addressObject);
        authorisationDetails.addProperty("accept_header", "text/html");
        authorisationDetails.addProperty("user_agent_header", "Mozilla/5.0");
        return toJson(authorisationDetails);
    }

    protected String buildJsonAuthorisationDetailsFor(String cardNumber, String cardBrand) {
        return buildJsonAuthorisationDetailsFor(cardNumber, "123", "11/99", cardBrand);
    }

    protected String buildJsonWithPaResponse() {
        JsonObject auth3dsDetails = new JsonObject();
        auth3dsDetails.addProperty("pa_response", "this-is-a-test-pa-response");

        return auth3dsDetails.toString();
    }

    protected String buildJsonAuthorisationDetailsFor(String cardHolderName, String cardNumber, String cardBrand) {
        return buildJsonAuthorisationDetailsFor(cardHolderName, cardNumber, "123", "11/99", cardBrand, "The Money Pool", null, "London", null, "DO11 4RS", "GB");
    }

    protected String buildJsonAuthorisationDetailsFor(String cardNumber, String cvc, String expiryDate, String cardBrand) {
        return buildJsonAuthorisationDetailsFor("Mr. Payment", cardNumber, cvc, expiryDate, cardBrand, "The Money Pool", null, "London", null, "DO11 4RS", "GB");
    }

    protected ValidatableResponse getCharge(String chargeId) {
        return getConnectorRestApi()
                .withChargeId(chargeId)
                .getCharge();
    }

    protected void assertFrontendChargeStatusIs(String chargeId, String status) {
        getConnectorRestApi()
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status));
    }

    protected void assertApiStateIs(String chargeId, String stateString) {
        getCharge(chargeId).body("state.status", is(stateString));
    }

    protected String authoriseNewCharge() {
        String externalChargeId = createNewChargeWithNoTransactionId(AUTHORISATION_SUCCESS);
        getApplication().getDatabaseTestHelper().updateChargeCardDetails(
                Long.parseLong(externalChargeId.replace("charge-", "")),
                CardFixture.aValidCard().withCardNo("1234").build());
        return externalChargeId;
    }


    protected String createNewCharge() {
        return createNewChargeWith(CREATED, "");
    }

    protected String createNewRefundWith(RefundStatus refundStatus, Long amount, Long chargeId, String transactionId) {
        long refundId = RandomUtils.nextInt();
        String externalRefundId = "refund-" + refundId;
        getApplication().getDatabaseTestHelper().addRefund(refundId, externalRefundId, transactionId, amount, refundStatus.getValue(), chargeId, ZonedDateTime.now());
        return externalRefundId;
    }

    protected String createNewChargeWithNoTransactionId(ChargeStatus status) {
        return createNewChargeWith(status, null);
    }

    protected String createNewCharge(ChargeStatus status) {
        return createNewChargeWith(status, randomId());
    }

    protected String createNewChargeWith(ChargeStatus status, String gatewayTransactionId) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        getApplication().getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, 6234L, status, "returnUrl", gatewayTransactionId);
        return externalChargeId;
    }

    protected RequestSpecification givenSetup() {
        return given().port(getApplication().getLocalPort())
                .contentType(JSON);
    }

    protected String buildJsonAuthorisationDetailsWithFullAddress() {
        return buildJsonAuthorisationDetailsFor(
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

    protected String buildJsonAuthorisationDetailsFor(String cardHolderName, String cardNumber, String cvc, String expiryDate, String cardBrand,
                                                      String line1, String line2, String city, String county, String postCode, String countryCode) {
        JsonObject addressObject = new JsonObject();

        addressObject.addProperty("line1", line1);
        addressObject.addProperty("line2", line2);
        addressObject.addProperty("city", city);
        addressObject.addProperty("county", county);
        addressObject.addProperty("postcode", postCode);
        addressObject.addProperty("country", countryCode);

        JsonObject authorisationDetails = new JsonObject();
        authorisationDetails.addProperty("card_number", cardNumber);
        authorisationDetails.addProperty("cvc", cvc);
        authorisationDetails.addProperty("expiry_date", expiryDate);
        authorisationDetails.addProperty("card_brand", cardBrand);
        authorisationDetails.addProperty("cardholder_name", cardHolderName);
        authorisationDetails.add("address", addressObject);
        authorisationDetails.addProperty("accept_header", "text/html");
        authorisationDetails.addProperty("user_agent_header", "Mozilla/5.0");
        return toJson(authorisationDetails);
    }

    protected void shouldReturnErrorForAuthorisationDetailsWithMessage(String authorisationDetails, String errorMessage, String status) throws Exception {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);

        givenSetup()
                .body(authorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", is(errorMessage));

        assertFrontendChargeStatusIs(chargeId, status);
    }

    protected String authoriseChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/cards".replace("{chargeId}", chargeId);
    }

    protected String authorise3dsChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/3ds".replace("{chargeId}", chargeId);
    }

    protected String captureChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/capture".replace("{chargeId}", chargeId);
    }

    protected String cancelChargeUrlFor(String accountId, String chargeId) {
        return "/v1/api/accounts/{accountId}/charges/{chargeId}/cancel".replace("{accountId}", accountId).replace("{chargeId}", chargeId);
    }

    protected String addCharge(ChargeStatus status, String reference, ZonedDateTime fromDate, String gatewayTransactionId) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        getApplication().getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, "http://somereturn.gov.uk",
                gatewayTransactionId, ServicePaymentReference.of(reference), fromDate);
        getApplication().getDatabaseTestHelper().addToken(chargeId, "tokenId");
        getApplication().getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());
        getApplication().getDatabaseTestHelper().updateChargeCardDetails(
                chargeId,
                CardFixture.aValidCard().withCardNo("1234").build());
        return externalChargeId;
    }

    protected Matcher<? super List<Map<String, Object>>> hasEvent(ChargeStatus chargeStatus) {
        return new TypeSafeMatcher<List<Map<String, Object>>>() {
            @Override
            protected boolean matchesSafely(List<Map<String, Object>> chargeEvents) {
                return chargeEvents.stream()
                        .anyMatch(chargeEvent ->
                                chargeStatus.getValue().equals(chargeEvent.get("status"))
                        );
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(format("no matching charge event with status [%s]", chargeStatus.getValue()));
            }
        };
    }

}
