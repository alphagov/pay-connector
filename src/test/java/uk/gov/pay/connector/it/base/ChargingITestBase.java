package uk.gov.pay.connector.it.base;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;
import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import uk.gov.pay.connector.model.ServicePaymentReference;
import uk.gov.pay.connector.model.domain.CardFixture;
import uk.gov.pay.connector.model.domain.PayersCardType;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.rules.EpdqMockClient;
import uk.gov.pay.connector.rules.SmartpayMockClient;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.PortFactory;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static io.dropwizard.testing.ConfigOverride.config;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class ChargingITestBase {

    private static final String ADDRESS_LINE_1 = "The Money Pool";
    private static final String ADDRESS_LINE_2 = "Moneybags Avenue";
    private static final String ADDRESS_CITY = "London";
    private static final String ADDRESS_COUNTY = "Greater London";
    private static final String ADDRESS_POSTCODE = "DO11 4RS";
    private static final String ADDRESS_COUNTRY_GB = "GB";
    private static final String CARD_HOLDER_NAME = "Scrooge McDuck";
    private static final String CARD_NUMBER = "4242424242424242";
    private static final String CVC = "123";
    private static final String EXPIRY_DATE = "11/99";
    private static final String CARD_BRAND = "cardBrand";
    protected RestAssuredClient connectorRestApi;
    protected static final long AMOUNT = 6234L;
    protected WorldpayMockClient worldpay;
    protected SmartpayMockClient smartpay;
    protected EpdqMockClient epdq;
    protected final String accountId;
    private final String paymentProvider;
    protected Map<String, String> credentials;
    private int port = PortFactory.findFreePort();

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("worldpay.urls.test", "http://localhost:" + port + "/jsp/merchant/xml/paymentService.jsp"),
            config("smartpay.urls.test", "http://localhost:" + port + "/pal/servlet/soap/Payment"),
            config("epdq.urls.test", "http://localhost:" + port + "/epdq")
    );
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    public ChargingITestBase(String paymentProvider) {
        this.paymentProvider = paymentProvider;
        this.accountId = String.valueOf(RandomUtils.nextInt());
    }

    @Before
    public void setup() {
        worldpay = new WorldpayMockClient();
        smartpay = new SmartpayMockClient();
        epdq = new EpdqMockClient();

        credentials = ImmutableMap.of(
                CREDENTIALS_MERCHANT_ID, "merchant-id",
                CREDENTIALS_USERNAME, "test-user",
                CREDENTIALS_PASSWORD, "test-password",
                CREDENTIALS_SHA_IN_PASSPHRASE, "test-sha-in-passphrase",
                CREDENTIALS_SHA_OUT_PASSPHRASE, "test-sha-out-passphrase"
        );
        app.getDatabaseTestHelper().addGatewayAccount(accountId, paymentProvider, credentials);
        connectorRestApi = new RestAssuredClient(app.getLocalPort(), accountId);
    }

    public Map<String, String> getCredentials() {
        return credentials;
    }

    protected static String authorisationDetailsWithMinimalAddress(String cardNumber, String cardBrand) {
        JsonObject addressObject = new JsonObject();

        addressObject.addProperty("line1", ADDRESS_LINE_1);
        addressObject.addProperty("city", ADDRESS_CITY);
        addressObject.addProperty("postcode", ADDRESS_POSTCODE);
        addressObject.addProperty("country", ADDRESS_COUNTRY_GB);

        JsonObject authorisationDetails = new JsonObject();
        authorisationDetails.addProperty("card_number", cardNumber);
        authorisationDetails.addProperty("cvc", CVC);
        authorisationDetails.addProperty("expiry_date", "12/21");
        authorisationDetails.addProperty("cardholder_name", "Mr. Payment");
        authorisationDetails.addProperty("card_brand", cardBrand);
        authorisationDetails.add("address", addressObject);
        authorisationDetails.addProperty("accept_header", "text/html");
        authorisationDetails.addProperty("user_agent_header", "Mozilla/5.0");
        return toJson(authorisationDetails);
    }

    protected static String buildJsonAuthorisationDetailsFor(String cardNumber, String cardBrand) {
        return buildJsonAuthorisationDetailsFor(cardNumber, CVC, EXPIRY_DATE, cardBrand);
    }

    protected static String buildJsonWithPaResponse() {
        JsonObject auth3dsDetails = new JsonObject();
        auth3dsDetails.addProperty("pa_response", "this-is-a-test-pa-response");

        return auth3dsDetails.toString();
    }

    protected static String buildJsonAuthorisationDetailsFor(String cardHolderName, String cardNumber, String cardBrand) {
        return buildJsonAuthorisationDetailsFor(cardHolderName, cardNumber, CVC, EXPIRY_DATE, cardBrand, ADDRESS_LINE_1, null, ADDRESS_CITY, null, ADDRESS_POSTCODE, ADDRESS_COUNTRY_GB);
    }

    protected static String buildJsonAuthorisationDetailsFor(String cardNumber, String cvc, String expiryDate, String cardBrand) {
        return buildJsonAuthorisationDetailsFor(CARD_HOLDER_NAME, cardNumber, cvc, expiryDate, cardBrand, ADDRESS_LINE_1, null, ADDRESS_CITY, null, ADDRESS_POSTCODE, ADDRESS_COUNTRY_GB);
    }

    protected static String buildDetailedJsonAuthorisationDetailsFor(String cardNumber, String cvc, String expiryDate, String cardBrand, String cardHolderName, String addressLine1, String addressLine2, String city, String county, String postcode, String country) {
        return buildJsonAuthorisationDetailsFor(cardHolderName, cardNumber, cvc, expiryDate, cardBrand, addressLine1, addressLine2, city, county, postcode, country);
    }

    protected ValidatableResponse getCharge(String chargeId) {
        return connectorRestApi
                .withChargeId(chargeId)
                .getCharge();
    }

    protected void assertFrontendChargeStatusIs(String chargeId, String status) {
        connectorRestApi
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status));
    }

    protected void assertRefundStatus(String chargeId, String refundId, String status, Integer amount) {
        connectorRestApi.withChargeId(chargeId)
                .withRefundId(refundId)
                .getRefund()
                .body("status", is(status))
                .body("amount", is(amount));
    }

    protected void assertApiStateIs(String chargeId, String stateString) {
        getCharge(chargeId).body("state.status", is(stateString));
    }

    protected String authoriseNewCharge() {
        String externalChargeId = createNewChargeWithNoTransactionId(AUTHORISATION_SUCCESS);
        app.getDatabaseTestHelper().updateChargeCardDetails(
                Long.parseLong(externalChargeId.replace("charge-", "")),
                CardFixture.aValidCard().build());
        return externalChargeId;
    }

    protected String createNewCharge() {
        return createNewChargeWith(CREATED, "");
    }

    protected String createNewRefundWith(RefundStatus refundStatus, Long amount, Long chargeId, String reference) {
        long refundId = RandomUtils.nextInt();
        String externalRefundId = "refund-" + refundId;
        app.getDatabaseTestHelper().addRefund(refundId, externalRefundId, reference, amount, refundStatus.getValue(), chargeId, ZonedDateTime.now());
        return externalRefundId;
    }

    protected String createNewChargeWithNoTransactionId(ChargeStatus status) {
        return createNewChargeWith(status, null);
    }

    protected String createNewChargeWithGatewayAccountId(ChargeStatus status, String gatewayAccountId) {
        return createNewChargeWith(status, null, gatewayAccountId);
    }

    protected String createNewCharge(ChargeStatus status) {
        return createNewChargeWith(status, randomId());
    }

    protected String createNewChargeWith(ChargeStatus status, String gatewayTransactionId) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, 6234L, status, "returnUrl", gatewayTransactionId);
        return externalChargeId;
    }

    protected String createNewChargeWith(ChargeStatus status, String gatewayTransactionId, String gatewayAccountId) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge-" + chargeId;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, gatewayAccountId, 6234L, status, "returnUrl", gatewayTransactionId);
        return externalChargeId;
    }

    protected void createNewRefund(RefundStatus status, long chargeId, String refundExternalId, String reference, long amount) {
        long refundId = RandomUtils.nextInt();
        app.getDatabaseTestHelper().addRefund(refundId, refundExternalId, reference, amount, status.getValue(), chargeId, ZonedDateTime.now());
    }

    protected RequestSpecification givenSetup() {
        return given().port(app.getLocalPort())
                .contentType(JSON);
    }

    protected static String buildJsonAuthorisationDetailsWithFullAddress() {
        return buildJsonAuthorisationDetailsFor(
                CARD_HOLDER_NAME,
                CARD_NUMBER,
                CVC,
                EXPIRY_DATE,
                CARD_BRAND,
                ADDRESS_LINE_1,
                ADDRESS_LINE_2,
                ADDRESS_CITY,
                ADDRESS_COUNTY,
                ADDRESS_POSTCODE,
                ADDRESS_COUNTRY_GB
        );
    }

    protected static String buildJsonAuthorisationDetailsFor(String cardHolderName, String cardNumber, String cvc, String expiryDate, String cardBrand,
                                                             String line1, String line2, String city, String county, String postCode, String countryCode) {
        return buildCorporateJsonAuthorisationDetailsFor(cardHolderName, cardNumber, cvc, expiryDate, cardBrand, line1, line2, city, county, postCode, countryCode,
                null, null);
    }

    private static String buildCorporateJsonAuthorisationDetailsFor(String cardHolderName, String cardNumber, String cvc, String expiryDate, String cardBrand,
                                                                    String line1, String line2, String city, String county, String postCode, String countryCode,
                                                                    Boolean isCorporateCard, PayersCardType payersCardType) {
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

        if (isCorporateCard != null) {
            authorisationDetails.addProperty("corporate_card", isCorporateCard);
        }
        if (payersCardType != null) {
            authorisationDetails.addProperty("card_type", payersCardType.toString());
        }

        return toJson(authorisationDetails);
    }

    protected static String buildCorporateJsonAuthorisationDetailsFor(PayersCardType payersCardType) {
        return buildCorporateJsonAuthorisationDetailsFor(
                CARD_HOLDER_NAME,
                CARD_NUMBER,
                CVC,
                EXPIRY_DATE, CARD_BRAND,
                ADDRESS_LINE_1,
                null,
                ADDRESS_CITY,
                null,
                ADDRESS_POSTCODE,
                ADDRESS_COUNTRY_GB,
                Boolean.TRUE,
                payersCardType);
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

    protected static String authoriseChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/cards".replace("{chargeId}", chargeId);
    }

    protected static String authorise3dsChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/3ds".replace("{chargeId}", chargeId);
    }

    protected static String captureChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/capture".replace("{chargeId}", chargeId);
    }

    protected static String cancelChargeUrlFor(String accountId, String chargeId) {
        return "/v1/api/accounts/{accountId}/charges/{chargeId}/cancel".replace("{accountId}", accountId).replace("{chargeId}", chargeId);
    }

    protected String addCharge(ChargeStatus status, String reference, ZonedDateTime fromDate, String gatewayTransactionId) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        app.getDatabaseTestHelper().addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, "http://somereturn.gov.uk",
                gatewayTransactionId, ServicePaymentReference.of(reference), fromDate);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");
        app.getDatabaseTestHelper().addEvent(chargeId, chargeStatus.getValue());
        app.getDatabaseTestHelper().updateChargeCardDetails(
                chargeId,
                CardFixture.aValidCard().build());
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

    protected String cancelChargeAndCheckApiStatus(String chargeId, ChargeStatus targetState, int targetHttpStatus) {

        connectorRestApi
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(targetHttpStatus); //assertion

        connectorRestApi
                .withChargeId(chargeId)
                .getCharge()
                .body("state.status", Matchers.is("cancelled"))
                .body("state.message", Matchers.is("Payment was cancelled by the service"))
                .body("state.code", Matchers.is("P0040"));

        connectorRestApi
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", Matchers.is(targetState.getValue()));

        return chargeId;
    }
}
