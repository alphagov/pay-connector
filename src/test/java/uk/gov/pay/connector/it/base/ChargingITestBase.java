package uk.gov.pay.connector.it.base;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.gson.JsonObject;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.rules.CardidStub;
import uk.gov.pay.connector.rules.LedgerStub;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.AddAgreementParams;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.AddPaymentInstrumentParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.util.RestAssuredClient;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.service.payments.commons.model.SupportedLanguage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_STRIPE_ACCOUNT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_MERCHANT_INITIATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.it.dao.DatabaseFixtures.withDatabaseTestHelper;
import static uk.gov.pay.connector.it.util.ChargeUtils.createNewChargeWithAccountId;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.TransactionId.randomId;
import static uk.gov.service.payments.commons.model.AuthorisationMode.WEB;

public class ChargingITestBase {

    protected static final String ADDRESS_LINE_1 = "The Money Pool";
    protected static final String ADDRESS_CITY = "London";
    protected static final String ADDRESS_POSTCODE = "DO11 4RS";
    protected static final String ADDRESS_COUNTRY_GB = "GB";
    protected static final String CVC = "123";

    protected static final String RETURN_URL = "http://service.local/success-page/";
    protected static final String EMAIL = randomAlphabetic(242) + "@example.com";
    protected static final long AMOUNT = 6234L;
    protected static final String JSON_REFERENCE_VALUE = "Test reference";
    protected static final String JSON_DESCRIPTION_VALUE = "Test description";

    protected static final String SERVICE_ID = "external-service-id";
    protected static final String JSON_PROVIDER_KEY = "payment_provider";
    protected static final String JSON_CREDENTIAL_ID_KEY = "credential_id";
    protected static final String PROVIDER_NAME = "sandbox";
    protected static final String JSON_CHARGE_KEY = "charge_id";
    protected static final String JSON_MESSAGE_KEY = "message";
    protected static final String JSON_AMOUNT_KEY = "amount";
    protected static final String JSON_REFERENCE_KEY = "reference";
    protected static final String JSON_DESCRIPTION_KEY = "description";
    protected static final String JSON_RETURN_URL_KEY = "return_url";
    protected static final String JSON_LANGUAGE_KEY = "language";
    protected static final String JSON_EMAIL_KEY = "email";
    protected static final String JSON_MOTO_KEY = "moto";
    protected static final String JSON_METADATA_KEY = "metadata";
    protected static final String JSON_AUTH_MODE_KEY = "authorisation_mode";
    protected static final String JSON_AUTH_MODE_MOTO_API = "moto_api";
    protected static final String JSON_DELAYED_CAPTURE_KEY = "delayed_capture";
    protected static final String JSON_SOURCE_KEY = "source";

    protected WorldpayMockClient worldpayMockClient;
    protected StripeMockClient stripeMockClient;
    protected LedgerStub ledgerStub;
    protected CardidStub cardidStub;

    private final String paymentProvider;
    protected RestAssuredClient connectorRestApiClient;
    protected final String accountId;
    protected final int gatewayAccountCredentialsId;
    protected Map<String, Object> credentials;
    private DatabaseFixtures.TestAccount testAccount;

    @DropwizardTestContext
    protected TestContext testContext;

    protected DatabaseTestHelper databaseTestHelper;

    protected WireMockServer wireMockServer;

    protected AddGatewayAccountCredentialsParams credentialParams;

    public ChargingITestBase(String paymentProvider) {
        this.paymentProvider = paymentProvider;
        this.accountId = String.valueOf(RandomUtils.nextInt());
        this.gatewayAccountCredentialsId = RandomUtils.nextInt();
    }

    @Before
    public void setUp() {
        wireMockServer = testContext.getWireMockServer();
        worldpayMockClient = new WorldpayMockClient(wireMockServer);
        stripeMockClient = new StripeMockClient(wireMockServer);
        ledgerStub = new LedgerStub(wireMockServer);
        cardidStub = new CardidStub(wireMockServer);

        if (paymentProvider.equals(STRIPE.getName())) {
            credentials = Map.of(CREDENTIALS_STRIPE_ACCOUNT_ID, "stripe-account-id");
        } else if (paymentProvider.equals(WORLDPAY.getName())) {
            credentials = Map.of(
                    ONE_OFF_CUSTOMER_INITIATED, Map.of(
                            CREDENTIALS_MERCHANT_CODE, "merchant-id",
                            CREDENTIALS_USERNAME, "test-user",
                            CREDENTIALS_PASSWORD, "test-password"),
                    RECURRING_CUSTOMER_INITIATED, Map.of(
                            CREDENTIALS_MERCHANT_CODE, "cit-merchant-id",
                            CREDENTIALS_USERNAME, "cit-user",
                            CREDENTIALS_PASSWORD, "cit-password"),
                    RECURRING_MERCHANT_INITIATED, Map.of(
                            CREDENTIALS_MERCHANT_CODE, "mit-merchant-id",
                            CREDENTIALS_USERNAME, "mit-user",
                            CREDENTIALS_PASSWORD, "mit-password")
            );
        } else {
            credentials = Map.of(
                    CREDENTIALS_MERCHANT_ID, "merchant-id",
                    CREDENTIALS_USERNAME, "test-user",
                    CREDENTIALS_PASSWORD, "test-password",
                    CREDENTIALS_SHA_IN_PASSPHRASE, "test-sha-in-passphrase",
                    CREDENTIALS_SHA_OUT_PASSPHRASE, "test-sha-out-passphrase"
            );
        }
        databaseTestHelper = testContext.getDatabaseTestHelper();
        ledgerStub.acceptPostEvent();

        credentialParams = anAddGatewayAccountCredentialsParams()
                .withId(gatewayAccountCredentialsId)
                .withPaymentProvider(paymentProvider)
                .withGatewayAccountId(Long.parseLong(accountId))
                .withState(ACTIVE)
                .withCredentials(credentials)
                .build();

        CardTypeEntity visaCreditCard = databaseTestHelper.getVisaCreditCard();
        testAccount = withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(Long.parseLong(accountId))
                .withPaymentProvider(getPaymentProvider())
                .withGatewayAccountCredentials(List.of(credentialParams))
                .withServiceId(SERVICE_ID)
                .withAllowAuthApi(true)
                .withCardTypeEntities(List.of(visaCreditCard))
                .insert();
        connectorRestApiClient = new RestAssuredClient(testContext.getPort(), accountId);
    }

    @After
    public void tearDown() {
        databaseTestHelper.truncateAllData();
    }

    public Map<String, Object> getCredentials() {
        return credentials;
    }

    protected static String authorisationDetailsWithMinimalAddress(String cardNumber, String cardBrand, String cardType) {
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
        authorisationDetails.addProperty("card_type", cardType);
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

    protected void assertFrontendChargeCorporateSurchargeAmount(String chargeId, String status, Long corporateSurcharge) {
        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status))
                .body("corporate_card_surcharge", is(corporateSurcharge.intValue()));
    }

    protected void assertFrontendChargeStatusAndTransactionId(String chargeId, String status) {
        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status))
                .body("gateway_transaction_id", is(notNullValue()));
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
                AuthCardDetailsFixture.anAuthCardDetails().build());
        return externalChargeId;
    }

    protected String createNewCharge() {
        return createNewChargeWith(CREATED, "");
    }

    protected String createNewChargeWithNoTransactionIdOrEmailAddress(ChargeStatus status) {
        return createNewChargeWithAccountId(status, null, accountId, databaseTestHelper, null, paymentProvider).toString();
    }

    protected String createNewChargeWithNoTransactionId(ChargeStatus status) {
        return createNewChargeWith(status, null);
    }

    protected String createNewCharge(ChargeStatus status) {
        return createNewChargeWith(status, randomId());
    }

    protected String createNewChargeWith(ChargeStatus status, String gatewayTransactionId) {
        return createNewChargeWithAccountId(status, gatewayTransactionId, accountId, databaseTestHelper, paymentProvider, credentialParams.getId()).toString();
    }

    protected RequestSpecification givenSetup() {
        return given().port(testContext.getPort())
                .contentType(JSON);
    }

    protected void shouldReturnErrorForAuthorisationDetailsWithMessage(String authorisationDetails, String errorMessage, String status) {

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);

        givenSetup()
                .body(authorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .body("message", contains(errorMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

        assertFrontendChargeStatusIs(chargeId, status);
    }

    public static String authoriseChargeUrlForApplePay(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/wallets/apple".replace("{chargeId}", chargeId);
    }

    public static String authoriseChargeUrlForGooglePay(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/wallets/google".replace("{chargeId}", chargeId);
    }

    public static String authoriseChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/cards".replace("{chargeId}", chargeId);
    }

    protected static String authorise3dsChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/3ds".replace("{chargeId}", chargeId);
    }

    protected static String captureChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/capture".replace("{chargeId}", chargeId);
    }

    protected static String captureUrlForAwaitingCaptureCharge(String accountId, String chargeId) {
        return "/v1/api/accounts/{accountId}/charges/{chargeId}/capture"
                .replace("{accountId}", accountId)
                .replace("{chargeId}", chargeId);
    }

    public static String cancelChargeUrlFor(String accountId, String chargeId) {
        return "/v1/api/accounts/{accountId}/charges/{chargeId}/cancel".replace("{accountId}", accountId).replace("{chargeId}", chargeId);
    }

    protected Matcher<? super List<Map<String, Object>>> hasEvent(ChargeStatus chargeStatus) {
        return new TypeSafeMatcher<>() {
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
                .body("state.status", is("cancelled"))
                .body("state.message", is("Payment was cancelled by the service"))
                .body("state.code", is("P0040"));

        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(targetState.getValue()))
                .body("state.status", is("cancelled"))
                .body("state.finished", is(true))
                .body("state.message", is("Payment was cancelled by the service"))
                .body("state.code", is("P0040"));

        connectorRestApiClient
                .withChargeId(chargeId)
                .getCharge()
                .body("status", emptyOrNullString())
                .body("state.status", is("cancelled"))
                .body("state.finished", is(true))
                .body("state.message", is("Payment was cancelled by the service"))
                .body("state.code", is("P0040"));

        return chargeId;
    }

    protected String addChargeAndCardDetails(ChargeStatus status, ServicePaymentReference reference, Instant fromDate) {
        return addChargeAndCardDetails(status, reference, fromDate, "");

    }

    protected String addChargeAndCardDetails(ChargeStatus status, ServicePaymentReference reference, Instant fromDate, String cardBrand) {
        return addChargeAndCardDetails(nextLong(), status, reference, fromDate, cardBrand);
    }

    protected String addCharge(ChargeStatus status) {
        return addCharge(status, "ref", Instant.now(), RandomIdGenerator.newId());
    }

    protected String addCharge(ChargeStatus status, String reference, Instant createdDate, String transactionId) {
        return addCharge(status, reference, createdDate, transactionId, "tokenId", WEB);
    }

    protected String addCharge(ChargeStatus status, String reference, Instant createdDate, String transactionId, String tokenId, AuthorisationMode authorisationMode) {
        long chargeId = RandomUtils.nextInt();
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        addCharge(chargeId, externalChargeId, status, ServicePaymentReference.of(reference), createdDate, transactionId, paymentProvider, authorisationMode);
        databaseTestHelper.addToken(chargeId, tokenId);
        databaseTestHelper.addEvent(chargeId, chargeStatus.getValue());
        databaseTestHelper.updateChargeCardDetails(
                chargeId,
                AuthCardDetailsFixture.anAuthCardDetails().build());
        return externalChargeId;
    }

    private void addCharge(long chargeId, String externalChargeId, ChargeStatus chargeStatus,
                           ServicePaymentReference reference, Instant createdDate, String transactionId,
                           String paymentProvider, AuthorisationMode authorisationMode) {
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId)
                .withGatewayAccountId(accountId)
                .withServiceId(SERVICE_ID)
                .withAmount(AMOUNT)
                .withPaymentProvider(paymentProvider)
                .withStatus(chargeStatus)
                .withTransactionId(transactionId)
                .withReference(reference)
                .withCreatedDate(createdDate)
                .withVersion(1)
                .withLanguage(SupportedLanguage.ENGLISH)
                .withDelayedCapture(false)
                .withEmail(EMAIL)
                .withGatewayCredentialId(credentialParams.getId())
                .withAuthorisationMode(authorisationMode)
                .build());
    }

    protected String addChargeAndCardDetails(Long chargeId, ChargeStatus status, ServicePaymentReference reference, Instant fromDate, String cardBrand) {
        String externalChargeId = "charge" + chargeId;
        ChargeStatus chargeStatus = status != null ? status : AUTHORISATION_SUCCESS;
        addCharge(chargeId, externalChargeId, chargeStatus, reference, fromDate, null, paymentProvider, WEB);
        databaseTestHelper.addToken(chargeId, "tokenId");
        databaseTestHelper.addEvent(chargeId, chargeStatus.getValue());
        databaseTestHelper.updateChargeCardDetails(chargeId, cardBrand, "1234", "123456", "Mr. McPayment",
                CardExpiryDate.valueOf("03/18"), null, "line1", null, "postcode", "city", null, "country");

        return externalChargeId;
    }

    protected ChargeUtils.ExternalChargeId addChargeForSetUpAgreement(ChargeStatus status) {
        String agreementExternalId = addAgreement();
        return addChargeForSetUpAgreement(status, agreementExternalId);
    }

    protected ChargeUtils.ExternalChargeId addChargeForSetUpAgreement(ChargeStatus status, String agreementExternalId) {
        return addChargeForSetUpAgreement(status, agreementExternalId, null);
    }
    
    protected ChargeUtils.ExternalChargeId addChargeForSetUpAgreement(ChargeStatus status, String agreementExternalId, Long paymentInstrumentId) {
        long chargeId = RandomUtils.nextInt();
        ChargeUtils.ExternalChargeId externalChargeId = ChargeUtils.ExternalChargeId.fromChargeId(chargeId);
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId.toString())
                .withGatewayAccountId(accountId)
                .withPaymentProvider(getPaymentProvider())
                .withAmount(6234L)
                .withStatus(status)
                .withEmail("email@fake.test")
                .withSavePaymentInstrumentToAgreement(true)
                .withAgreementExternalId(agreementExternalId)
                .withGatewayCredentialId((long) gatewayAccountCredentialsId)
                .withPaymentInstrumentId(paymentInstrumentId)
                .build());
        return externalChargeId;
    }

    protected String addAgreement() {
        String agreementExternalId = String.valueOf(nextLong());
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(agreementExternalId)
                .build();
        databaseTestHelper.addAgreement(agreementParams);
        return agreementExternalId;
    }
    
    protected Long addPaymentInstrument(String agreementExternalId, PaymentInstrumentStatus status) {
        Long paymentInstrumentId = nextLong();
        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withAgreementExternalId(agreementExternalId)
                .withPaymentInstrumentStatus(status)
                .build();
        databaseTestHelper.addPaymentInstrument(paymentInstrumentParams);
        return paymentInstrumentId;
    }

    protected ChargeUtils.ExternalChargeId addChargeWithAuthorisationModeAgreement(Map<String, String> recurringAuthToken) {
        return addChargeWithAuthorisationModeAgreement(null, null, recurringAuthToken);
    }

    protected ChargeUtils.ExternalChargeId addChargeWithAuthorisationModeAgreement(
            FirstDigitsCardNumber first6DigitsCardNumber,
            LastDigitsCardNumber last4DigitsCardNumber,
            Map<String, String> recurringAuthToken
    ) {
        String agreementExternalId = addAgreement();

        long paymentInstrumentId = nextInt();
        AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder paymentInstrumentParamsBuilder = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withExternalPaymentInstrumentId(String.valueOf(nextInt()))
                .withRecurringAuthToken(recurringAuthToken)
                .withAgreementExternalId(agreementExternalId);

        Optional.ofNullable(first6DigitsCardNumber).ifPresent(paymentInstrumentParamsBuilder::withFirstDigitsCardNumber);
        Optional.ofNullable(last4DigitsCardNumber).ifPresent(paymentInstrumentParamsBuilder::withLastDigitsCardNumber);

        databaseTestHelper.addPaymentInstrument(paymentInstrumentParamsBuilder.build());
        databaseTestHelper.updateAgreementPaymentInstrumentId(agreementExternalId, paymentInstrumentId);

        long chargeId = RandomUtils.nextInt();
        ChargeUtils.ExternalChargeId externalChargeId = ChargeUtils.ExternalChargeId.fromChargeId(chargeId);
        var chargeParams = anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId.toString())
                .withPaymentProvider(getPaymentProvider())
                .withGatewayAccountId(accountId)
                .withGatewayCredentialId((long) gatewayAccountCredentialsId)
                .withAgreementExternalId(agreementExternalId)
                .withAmount(10000)
                .withStatus(ChargeStatus.CREATED)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withPaymentInstrumentId(paymentInstrumentId);
        databaseTestHelper.addCharge(chargeParams.build());

        return externalChargeId;
    }

    protected String getPaymentProvider() {
        return paymentProvider;
    }

    protected DatabaseFixtures.TestAccount getTestAccount() {
        return testAccount;
    }
}
