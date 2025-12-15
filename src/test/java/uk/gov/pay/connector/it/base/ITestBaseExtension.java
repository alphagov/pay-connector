package uk.gov.pay.connector.it.base;

import com.google.gson.JsonObject;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.util.AddAgreementParams;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.AddPaymentInstrumentParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;
import uk.gov.service.payments.commons.model.AgreementPaymentType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.service.payments.commons.model.SupportedLanguage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
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
import static uk.gov.pay.connector.util.RandomGeneratorUtils.randomAlphabetic;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.secureRandomInt;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.secureRandomLong;
import static uk.gov.pay.connector.util.TransactionId.randomId;

public class ITestBaseExtension implements BeforeEachCallback, BeforeAllCallback, AfterEachCallback, AfterAllCallback {
    public static final String ADDRESS_LINE_1 = "The Money Pool";
    public static final String ADDRESS_CITY = "London";
    public static final String ADDRESS_POSTCODE = "DO11 4RS";
    public static final String ADDRESS_COUNTRY_GB = "GB";
    public static final String CVC = "123";

    public static final String RETURN_URL = "http://service.local/success-page/";
    public static final String EMAIL = randomAlphabetic(242) + "@example.com";
    public static final long AMOUNT = 6234L;
    public static final String JSON_REFERENCE_VALUE = "Test reference";
    public static final String JSON_DESCRIPTION_VALUE = "Test description";

    public static final String SERVICE_ID = "external-service-id";
    public static final String JSON_PROVIDER_KEY = "payment_provider";
    public static final String JSON_CREDENTIAL_ID_KEY = "credential_id";
    public static final String PROVIDER_NAME = "sandbox";
    public static final String JSON_CHARGE_KEY = "charge_id";
    public static final String JSON_MESSAGE_KEY = "message";
    public static final String JSON_AMOUNT_KEY = "amount";
    public static final String JSON_REFERENCE_KEY = "reference";
    public static final String JSON_DESCRIPTION_KEY = "description";
    public static final String JSON_RETURN_URL_KEY = "return_url";
    public static final String JSON_LANGUAGE_KEY = "language";
    public static final String JSON_EMAIL_KEY = "email";
    public static final String JSON_MOTO_KEY = "moto";
    public static final String JSON_METADATA_KEY = "metadata";
    public static final String JSON_AUTH_MODE_KEY = "authorisation_mode";
    public static final String JSON_AUTH_MODE_MOTO_API = "moto_api";
    public static final String JSON_DELAYED_CAPTURE_KEY = "delayed_capture";
    public static final String JSON_SOURCE_KEY = "source";

    private String paymentProvider;

    protected static String accountId = String.valueOf(secureRandomInt());
    private int gatewayAccountCredentialsId = secureRandomInt();
    private Map<String, Object> credentials;
    private DatabaseFixtures.TestAccount testAccount;
    private static AddGatewayAccountCredentialsParams credentialParams;
    public RestAssuredClient connectorRestApiClient;
    private final DatabaseTestHelper databaseTestHelper;
    private final int appLocalPort;

    public ITestBaseExtension(String paymentProvider, int appLocalPort, DatabaseTestHelper databaseTestHelper) {
        this.paymentProvider = paymentProvider;
        this.databaseTestHelper = databaseTestHelper;
        this.appLocalPort = appLocalPort;
        createCredentialParams();
    }

    private void setUpBase() {
        createConnectorRestApiClient();
        createTestAccount();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        setUpBase();
    }

    @Override
    public void afterEach(ExtensionContext context) {
    }

    @Override
    public void beforeAll(ExtensionContext context) {
    }

    @Override
    public void afterAll(ExtensionContext context) {
    }

    public void createConnectorRestApiClient() {
        connectorRestApiClient = new RestAssuredClient(appLocalPort, accountId);
    }

    public void createTestAccount() {
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
    }

    public void createCredentialParams() {
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

        credentialParams = anAddGatewayAccountCredentialsParams()
                .withId(gatewayAccountCredentialsId)
                .withPaymentProvider(paymentProvider)
                .withGatewayAccountId(Long.parseLong(accountId))
                .withState(ACTIVE)
                .withCredentials(credentials)
                .build();
    }

    public Map<String, Object> getCredentials() {
        return credentials;
    }

    public static String authorisationDetailsWithMinimalAddress(String cardNumber, String cardBrand, String cardType) {
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

    public static String buildJsonWithPaResponse() {
        JsonObject auth3dsDetails = new JsonObject();
        auth3dsDetails.addProperty("pa_response", "this-is-a-test-pa-response");

        return auth3dsDetails.toString();
    }

    public ValidatableResponse getCharge(String chargeId) {
        return connectorRestApiClient
                .withChargeId(chargeId)
                .getCharge();
    }

    public void assertFrontendChargeStatusIs(String chargeId, String status) {
        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status));
    }

    public void assertFrontendChargeCorporateSurchargeAmount(String chargeId, String status, Long corporateSurcharge) {
        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status))
                .body("corporate_card_surcharge", is(corporateSurcharge.intValue()));
    }

    public void assertFrontendChargeStatusAndTransactionId(String chargeId, String status) {
        connectorRestApiClient
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(status))
                .body("gateway_transaction_id", is(notNullValue()));
    }

    public void assertRefundStatus(String chargeId, String refundId, String status, Integer amount) {
        connectorRestApiClient.withChargeId(chargeId)
                .withRefundId(refundId)
                .getRefund()
                .body("status", is(status))
                .body("amount", is(amount));
    }

    public void assertApiStateIs(String chargeId, String stateString) {
        getCharge(chargeId).body("state.status", is(stateString));
    }

    public void assertAgreementPaymentTypeIs(String chargeId, AgreementPaymentType agreementPaymentType) {
        var charge = databaseTestHelper.getChargeByExternalId(chargeId);
        assertThat(charge.get("agreement_payment_type"), is(agreementPaymentType.toString()));
    }

    public String authoriseNewCharge() {
        String externalChargeId = createNewChargeWithNoTransactionId(AUTHORISATION_SUCCESS);
        databaseTestHelper.updateChargeCardDetails(
                Long.parseLong(externalChargeId.replace("charge-", "")),
                AuthCardDetailsFixture.anAuthCardDetails().build());
        return externalChargeId;
    }

    public String authoriseNewChargeWithServiceId(String serviceId) {
        String externalChargeId = createNewChargeWithNoTransactionId(AUTHORISATION_SUCCESS);
        databaseTestHelper.updateChargeCardDetails(
                Long.parseLong(externalChargeId.replace("charge-", "")),
                AuthCardDetailsFixture.anAuthCardDetails().build());
        databaseTestHelper.updateServiceIdForCharge(externalChargeId, serviceId);
        return externalChargeId;
    }

    public String createNewCharge() {
        return createNewChargeWith(CREATED, "");
    }

    public String createNewChargeWithServiceId(String serviceId) {
        return createNewChargeWithServiceIdAndStatus(serviceId, CREATED);
    }

    public String createNewChargeWithServiceIdAndStatus(String serviceId, ChargeStatus chargeStatus) {
        String chargeExternalId = createNewChargeWith(chargeStatus, "");
        databaseTestHelper.updateServiceIdForCharge(chargeExternalId, serviceId);
        return chargeExternalId;
    }

    public String createNewChargeWithNoGatewayTransactionIdOrEmailAddress(ChargeStatus status) {
        return createNewChargeWithAccountId(status, null, accountId, databaseTestHelper, null, paymentProvider).toString();
    }

    public String createNewChargeWithDescriptionAndNoGatewayTransactionIdOrEmailAddress(ChargeStatus status, String description) {
        return createNewChargeWithAccountId(status, null, accountId, databaseTestHelper, null, paymentProvider, description).toString();
    }

    public String createNewChargeWithNoTransactionId(ChargeStatus status) {
        return createNewChargeWith(status, null);
    }

    public String createNewCharge(ChargeStatus status) {
        return createNewChargeWith(status, randomId());
    }

    public String createNewChargeWith(ChargeStatus status, String gatewayTransactionId) {
        return createNewChargeWithAccountId(status, gatewayTransactionId, accountId, databaseTestHelper, paymentProvider, credentialParams.getId()).toString();
    }

    public RequestSpecification givenSetup() {
        return given().port(appLocalPort)
                .contentType(JSON);
    }

    public void shouldReturnErrorForAuthorisationDetailsWithMessage(String authorisationDetails, String errorMessage, String status) {

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

    public static String authoriseChargeUrlForGooglePayStripe(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/wallets/google/stripe".replace("{chargeId}", chargeId);
    }

    public static String authoriseChargeUrlForGooglePayWorldpay(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/wallets/google/worldpay".replace("{chargeId}", chargeId);
    }

    public static String authoriseChargeUrlForGooglePay(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/wallets/google".replace("{chargeId}", chargeId);
    }


    public static String authoriseChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/cards".replace("{chargeId}", chargeId);
    }

    public static String authorise3dsChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/3ds".replace("{chargeId}", chargeId);
    }

    public static String captureChargeUrlFor(String chargeId) {
        return "/v1/frontend/charges/{chargeId}/capture".replace("{chargeId}", chargeId);
    }

    public static String cancelChargeUrlFor(String accountId, String chargeId) {
        return "/v1/api/accounts/{accountId}/charges/{chargeId}/cancel".replace("{accountId}", accountId).replace("{chargeId}", chargeId);
    }

    public Matcher<? super List<Map<String, Object>>> hasEvent(ChargeStatus chargeStatus) {
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

    public String cancelChargeAndCheckApiStatus(String chargeId, ChargeStatus targetState, int targetHttpStatus) {
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

    public String addCharge(AddChargeParameters addChargeParameters) {
        addChargeToDatabase(addChargeParameters);
        databaseTestHelper.addToken(addChargeParameters.chargeId(), "tokenId");
        databaseTestHelper.addEvent(addChargeParameters.chargeId(), addChargeParameters.chargeStatus().getValue());
        databaseTestHelper.updateChargeCardDetails(
                addChargeParameters.chargeId(),
                AuthCardDetailsFixture.anAuthCardDetails().build());
        return addChargeParameters.externalChargeId();
    }

    private void addChargeToDatabase(AddChargeParameters addChargeParameters) {
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(addChargeParameters.chargeId())
                .withExternalChargeId(addChargeParameters.externalChargeId())
                .withGatewayAccountId(accountId)
                .withServiceId(SERVICE_ID)
                .withAmount(AMOUNT)
                .withPaymentProvider(paymentProvider)
                .withStatus(addChargeParameters.chargeStatus())
                .withTransactionId(addChargeParameters.transactionId())
                .withReference(addChargeParameters.reference())
                .withCreatedDate(addChargeParameters.createdDate())
                .withVersion(1)
                .withLanguage(SupportedLanguage.ENGLISH)
                .withDelayedCapture(false)
                .withEmail(EMAIL)
                .withGatewayCredentialId(credentialParams.getId())
                .withAuthorisationMode(addChargeParameters.authorisationMode())
                .build());
    }

    public ChargeUtils.ExternalChargeId addChargeForSetUpAgreement(ChargeStatus status) {
        String agreementExternalId = addAgreement();
        return addChargeForSetUpAgreement(status, agreementExternalId);
    }

    public ChargeUtils.ExternalChargeId addChargeForSetUpAgreement(ChargeStatus status, String agreementExternalId) {
        return addChargeForSetUpAgreement(status, agreementExternalId, null);
    }

    public ChargeUtils.ExternalChargeId addChargeForSetUpAgreement(ChargeStatus status, String agreementExternalId, Long paymentInstrumentId) {
        long chargeId = secureRandomInt();
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

    public String addAgreement() {
        String agreementExternalId = String.valueOf(secureRandomLong());
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(agreementExternalId)
                .build();
        databaseTestHelper.addAgreement(agreementParams);
        return agreementExternalId;
    }

    public Long addPaymentInstrument(String agreementExternalId, PaymentInstrumentStatus status) {
        Long paymentInstrumentId = secureRandomLong();
        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withAgreementExternalId(agreementExternalId)
                .withPaymentInstrumentStatus(status)
                .build();
        databaseTestHelper.addPaymentInstrument(paymentInstrumentParams);
        return paymentInstrumentId;
    }

    public ChargeUtils.ExternalChargeId addChargeWithAuthorisationModeAgreement(Map<String, String> recurringAuthToken) {
        return addChargeWithAuthorisationModeAgreement(null, null, recurringAuthToken);
    }

    public ChargeUtils.ExternalChargeId addChargeWithAuthorisationModeAgreement(
            FirstDigitsCardNumber first6DigitsCardNumber,
            LastDigitsCardNumber last4DigitsCardNumber,
            Map<String, String> recurringAuthToken
    ) {
        String agreementExternalId = addAgreement();

        long paymentInstrumentId = secureRandomInt();
        AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder paymentInstrumentParamsBuilder = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withExternalPaymentInstrumentId(String.valueOf(secureRandomInt()))
                .withRecurringAuthToken(recurringAuthToken)
                .withAgreementExternalId(agreementExternalId);

        Optional.ofNullable(first6DigitsCardNumber).ifPresent(paymentInstrumentParamsBuilder::withFirstDigitsCardNumber);
        Optional.ofNullable(last4DigitsCardNumber).ifPresent(paymentInstrumentParamsBuilder::withLastDigitsCardNumber);

        databaseTestHelper.addPaymentInstrument(paymentInstrumentParamsBuilder.build());
        databaseTestHelper.updateAgreementPaymentInstrumentId(agreementExternalId, paymentInstrumentId);

        long chargeId = secureRandomInt();
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

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public DatabaseFixtures.TestAccount getTestAccount() {
        return testAccount;
    }

    public RestAssuredClient getConnectorRestApiClient() {
        return connectorRestApiClient;
    }

    public String getAccountId() {
        return accountId;
    }

    public int getGatewayAccountCredentialsId() {
        return gatewayAccountCredentialsId;
    }

    public AddGatewayAccountCredentialsParams getCredentialParams() {
        return credentialParams;
    }
}
