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
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.model.domain.CardFixture;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.rules.EpdqMockClient;
import uk.gov.pay.connector.rules.SmartpayMockClient;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.junit.DropwizardJUnitRunner.WIREMOCK_PORT;
import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class ChargingITestBase {

    private static final String ADDRESS_LINE_1 = "The Money Pool";
    private static final String ADDRESS_CITY = "London";
    private static final String ADDRESS_POSTCODE = "DO11 4RS";
    private static final String ADDRESS_COUNTRY_GB = "GB";
    private static final String CVC = "123";

    protected static final String RETURN_URL = "http://service.url/success-page/";
    protected static final String EMAIL = randomAlphabetic(242) + "@example.com";
    protected static final long AMOUNT = 6234L;
    protected static final WorldpayMockClient worldpayMockClient = new WorldpayMockClient();
    protected static final SmartpayMockClient smartpayMockClient = new SmartpayMockClient();
    protected static final EpdqMockClient epdqMockClient = new EpdqMockClient();

    private final String paymentProvider;
    protected RestAssuredClient connectorRestApiClient;
    protected final String accountId;
    protected Map<String, String> credentials;

    @DropwizardTestContext
    protected TestContext testContext;

    protected DatabaseTestHelper databaseTestHelper;

    @ClassRule
    public static WireMockRule wireMockRule = new WireMockRule(WIREMOCK_PORT);

    public ChargingITestBase(String paymentProvider) {
        this.paymentProvider = paymentProvider;
        this.accountId = String.valueOf(RandomUtils.nextInt());
    }

    @Before
    public void setup() {
        credentials = ImmutableMap.of(
                CREDENTIALS_MERCHANT_ID, "merchant-id",
                CREDENTIALS_USERNAME, "test-user",
                CREDENTIALS_PASSWORD, "test-password",
                CREDENTIALS_SHA_IN_PASSPHRASE, "test-sha-in-passphrase",
                CREDENTIALS_SHA_OUT_PASSPHRASE, "test-sha-out-passphrase"
        );
        databaseTestHelper = testContext.getDatabaseTestHelper();
        databaseTestHelper.addGatewayAccount(accountId, paymentProvider, credentials);
        connectorRestApiClient = new RestAssuredClient(testContext.getPort(), accountId);
    }

    @After
    public void tearDown() {
        databaseTestHelper.truncateAllData();
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

    protected static String buildJsonWithPaResponse() {
        JsonObject auth3dsDetails = new JsonObject();
        auth3dsDetails.addProperty("pa_response", "this-is-a-test-pa-response");

        return auth3dsDetails.toString();
    }

    protected ValidatableResponse getCharge(String chargeId) {
        return connectorRestApiClient
                .withChargeId(chargeId)
                .getCharge();
    }

    protected void assertFrontendChargeStatusIs(String chargeId, String status) {
        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status));
    }

    protected void assertRefundStatus(String chargeId, String refundId, String status, Integer amount) {
        connectorRestApiClient.withChargeId(chargeId)
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
        databaseTestHelper.updateChargeCardDetails(
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
        databaseTestHelper.addRefund(refundId, externalRefundId, reference, amount, refundStatus.getValue(), chargeId, ZonedDateTime.now());
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
        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, 6234L, status, "RETURN_URL", gatewayTransactionId);
        return externalChargeId;
    }

    protected void createNewRefund(RefundStatus status, long chargeId, String refundExternalId, String reference, long amount) {
        long refundId = RandomUtils.nextInt();
        databaseTestHelper.addRefund(refundId, refundExternalId, reference, amount, status.getValue(), chargeId, ZonedDateTime.now());
    }

    protected RequestSpecification givenSetup() {
        return given().port(testContext.getPort())
                .contentType(JSON);
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
        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, "http://somereturn.gov.uk",
                gatewayTransactionId, ServicePaymentReference.of(reference), fromDate);
        databaseTestHelper.addToken(chargeId, "tokenId");
        databaseTestHelper.addEvent(chargeId, chargeStatus.getValue());
        databaseTestHelper.updateChargeCardDetails(
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

        connectorRestApiClient
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(targetHttpStatus); //assertion

        connectorRestApiClient
                .withChargeId(chargeId)
                .getCharge()
                .body("state.status", Matchers.is("cancelled"))
                .body("state.message", Matchers.is("Payment was cancelled by the service"))
                .body("state.code", Matchers.is("P0040"));

        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", Matchers.is(targetState.getValue()));

        return chargeId;
    }

    protected String addChargeAndCardDetails(ChargeStatus status, ServicePaymentReference reference, ZonedDateTime fromDate) {
        return addChargeAndCardDetails(status, reference, fromDate, "");

    }

    protected String addChargeAndCardDetails(ChargeStatus status, ServicePaymentReference reference, ZonedDateTime fromDate, String cardBrand) {
        return addChargeAndCardDetails(nextLong(), status, reference, fromDate, cardBrand);
    }

    protected String addChargeAndCardDetails(Long chargeId, ChargeStatus status, ServicePaymentReference reference, ZonedDateTime fromDate, String cardBrand) {
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        databaseTestHelper.addCharge(chargeId, externalChargeId, accountId, AMOUNT, chargeStatus, RETURN_URL, null, reference, fromDate, EMAIL);
        databaseTestHelper.addToken(chargeId, "tokenId");
        databaseTestHelper.addEvent(chargeId, chargeStatus.getValue());
        databaseTestHelper.updateChargeCardDetails(chargeId, cardBrand, "1234", "123456", "Mr. McPayment", "03/18", "line1", null, "postcode", "city", null, "country");

        return externalChargeId;
    }

    protected List<String> collect(List<Map<String, Object>> results, String field) {
        return results.stream().map(result -> result.get(field).toString()).collect(Collectors.toList());
    }

    protected List<ZonedDateTime> datesFrom(List<String> createdDateStrings) {
        List<ZonedDateTime> dateTimes = newArrayList();
        createdDateStrings.forEach(aDateString -> dateTimes.add(toUTCZonedDateTime(aDateString).get()));
        return dateTimes;
    }
}
