package uk.gov.pay.connector.it.resources.adyen;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.github.netmikey.logunit.api.LogCapturer;
import org.apache.http.auth.AUTH;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.org.checkerframework.checker.nullness.qual.Nullable;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.pay.connector.queue.tasks.handlers.AuthoriseWithUserNotPresentHandler;
import uk.gov.pay.connector.util.AddAgreementParams;
import uk.gov.pay.connector.util.AddPaymentInstrumentParams;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.http.ContentType.JSON;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_USER_NOT_PRESENT_QUEUED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_QUEUED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.gateway.adyen.AdyenRecurringAuthTokenKeys.STORED_PAYMENT_METHOD_ID;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.AMOUNT;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AMOUNT_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AUTH_MODE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_VALUE;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_VALUE;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.authorise3dsChargeUrlFor;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;
import static uk.gov.service.payments.commons.model.AgreementPaymentType.RECURRING;

@ExtendWith(DropwizardExtensionsSupport.class)
class AdyenCardResourceAuthoriseRecurringPaymentsIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("adyen", app.getLocalPort(), app.getDatabaseTestHelper());

    private ChargeDao chargeDao;

    private final String externalAgreementId = "agreement-external-id-123";

    private AuthCardDetails authCardDetails;

    private static final String REDIRECT_RESULT = "eyJ0cmFuc1N0YXR1cyI6IlkifQ==";

    private static final String PSP_REFERENCE_FROM_ADYEN = "993617895215577D";

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(CaptureQueue.class);

    private static final String JSON_AGREEMENT_ID_KEY = "agreement_id";
    private static final String JSON_VALID_AGREEMENT_ID_VALUE = "12345678901234567890123456";
    private static final String JSON_AUTH_MODE_AGREEMENT = "agreement";


    @BeforeEach
    void setUp() {
        chargeDao = app.getInstanceFromGuiceContainer(ChargeDao.class);
    }

    @Test
    void successful_authorisation_of_a_recurring_payment() {
        var chargeId = createChargeWithAgreement(ENTERING_CARD_DETAILS);

        app.getAdyenCheckoutMockClient().mockAuthorisationSuccess(PSP_REFERENCE_FROM_ADYEN);

        verifyResponseForRecurringPayment(chargeId);

        getAndVerifyChargeEntity(chargeId, PSP_REFERENCE_FROM_ADYEN);
    }

    @Test
    void successful_creation_of_payment_instrument_with_recurring_auth_token() {
        var chargeId = createChargeWithAgreement(ENTERING_CARD_DETAILS);
        var expectedStoredPaymentMethodId = "M5N7TQ4TG5PFWR50";

        app.getAdyenCheckoutMockClient().mockAuthorisationSuccessForRecurringPayment(PSP_REFERENCE_FROM_ADYEN, expectedStoredPaymentMethodId);

        verifyResponseForRecurringPayment(chargeId);

        Optional<ChargeEntity> charge = getAndVerifyChargeEntity(chargeId, PSP_REFERENCE_FROM_ADYEN);

        var storedPaymentMethodId = getStoredPaymentMethodId(charge);

        assertThat(storedPaymentMethodId, is(expectedStoredPaymentMethodId));
    }

    @Test
    void shouldProcess_ACorrectlyConfiguredAuthorisationModeAgreementCharge_AndMarkForCapture() {
        AuthoriseWithUserNotPresentHandler taskHandler = app.getInstanceFromGuiceContainer(AuthoriseWithUserNotPresentHandler.class);
        String storedPaymentMethodId = "4242";
        var chargeId = createChargeWithAgreement(ENTERING_CARD_DETAILS);

        String chargeWithValidAgreementAndPaymentInstrument = setupChargeWithAgreementAndPaymentInstrument(storedPaymentMethodId);

        app.getAdyenCheckoutMockClient().mockAuthorisationSuccessForRecurringPayment(PSP_REFERENCE_FROM_ADYEN, storedPaymentMethodId);

        verifyResponseForRecurringPayment(chargeId);

        taskHandler.process(chargeWithValidAgreementAndPaymentInstrument);

        testBaseExtension.assertFrontendChargeStatusIs(chargeWithValidAgreementAndPaymentInstrument, CAPTURE_QUEUED.getValue());
        testBaseExtension.assertApiStateIs(chargeWithValidAgreementAndPaymentInstrument, EXTERNAL_SUCCESS.getStatus());

        logs.assertContains("Charge [" + chargeWithValidAgreementAndPaymentInstrument + "] added to capture queue.");
    }

    @Test
    void successful_creation_of_payment_instrument_without_recurring_auth_token() {
        var chargeId = createChargeWithAgreement(ENTERING_CARD_DETAILS);
        app.getAdyenCheckoutMockClient().mockAuthorisationSuccess(PSP_REFERENCE_FROM_ADYEN);

        verifyResponseForRecurringPayment(chargeId);

        Optional<ChargeEntity> charge = getAndVerifyChargeEntity(chargeId, PSP_REFERENCE_FROM_ADYEN);

        var paymentInstrument = charge.get().getPaymentInstrument();

        assertThat(paymentInstrument.isEmpty(), is(false));
        assertThat(paymentInstrument.get().getRecurringAuthToken().isEmpty(), is(true));
    }

    @Test
    void successful_creation_of_payment_instrument_for_3ds_with_recurring_auth_token() {
        var chargeId = createChargeWithAgreement(AUTHORISATION_3DS_REQUIRED);
        var expectedStoredPaymentMethodId = "M5N7TQ4TG5PFWR50";

        app.getAdyenCheckoutMockClient().mock3dsAuthorisationResponseForRecurringPayment(PSP_REFERENCE_FROM_ADYEN, "Authorised", expectedStoredPaymentMethodId);

        verifyResponseForRecurringPaymentWith3ds(chargeId, 200, AUTHORISATION_SUCCESS.getValue());

        Optional<ChargeEntity> charge = getAndVerifyChargeEntity(chargeId, PSP_REFERENCE_FROM_ADYEN);

        var storedPaymentMethodId = getStoredPaymentMethodId(charge);

        assertThat(storedPaymentMethodId, is(expectedStoredPaymentMethodId));
    }

    @Test
    void successful_creation_of_payment_instrument_for_3ds_without_recurring_auth_token() {
        var chargeId = createChargeWithAgreement(AUTHORISATION_3DS_REQUIRED);

        app.getAdyenCheckoutMockClient().mock3dsAuthorisationResponse(PSP_REFERENCE_FROM_ADYEN, "Authorised");

        verifyResponseForRecurringPaymentWith3ds(chargeId, 200, AUTHORISATION_SUCCESS.getValue());

        Optional<ChargeEntity> charge = getAndVerifyChargeEntity(chargeId, PSP_REFERENCE_FROM_ADYEN);

        var paymentInstrument = charge.get().getPaymentInstrument();

        assertThat(paymentInstrument.isEmpty(), is(false));
        assertThat(paymentInstrument.get().getRecurringAuthToken().isEmpty(), is(true));
    }

    @Test
    void errored_recurring_payment_should_not_create_a_paymentInstrument() {
        var chargeId = createChargeWithAgreement(ENTERING_CARD_DETAILS);

        app.getAdyenCheckoutMockClient().mockAuthorisationError(PSP_REFERENCE_FROM_ADYEN);

        verifyDeclineErrorResponseForRecurringPayment(chargeId, 402, "There was an error authorising the transaction.");

        getAndVerifyChargeEntityForFailedAuthorisation(chargeId, "AUTHORISATION ERROR", PSP_REFERENCE_FROM_ADYEN);
    }

    @Test
    void rejected_recurring_payment_should_not_create_a_paymentInstrument() {
        var chargeId = createChargeWithAgreement(ENTERING_CARD_DETAILS);

        app.getAdyenCheckoutMockClient().mockAuthorisationRejected(PSP_REFERENCE_FROM_ADYEN);

        verifyDeclineErrorResponseForRecurringPayment(chargeId, 400, "This transaction was declined.");

        getAndVerifyChargeEntityForFailedAuthorisation(chargeId, "AUTHORISATION REJECTED", PSP_REFERENCE_FROM_ADYEN);
    }

    @Test
    void errored_3ds_recurring_payment_should_not_create_a_paymentInstrument() {
        var chargeId = createChargeWithAgreement(AUTHORISATION_3DS_REQUIRED);

        app.getAdyenCheckoutMockClient().mock3dsAuthorisationClientError();

        verifyResponseForRecurringPaymentWith3ds(chargeId, 402, null);

        getAndVerifyChargeEntityForFailedAuthorisation(chargeId, "AUTHORISATION ERROR", null);
    }

    private String createChargeWithAgreement(ChargeStatus chargeStatus) {
        app.getDatabaseTestHelper().enableRecurring(Long.parseLong(testBaseExtension.getAccountId()));

        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(String.valueOf(testBaseExtension.getAccountId()))
                .withExternalAgreementId(externalAgreementId)
                .withReference("test reference").build();

        app.getDatabaseTestHelper().addAgreement(agreementParams);

        var chargeId = testBaseExtension.addChargeForSetUpAgreement(chargeStatus, externalAgreementId, null, RECURRING).toString();

        authCardDetails = anAuthCardDetails()
                .withCardNo("4444333322221111")
                .withCardBrand("Visa")
                .withCardHolder("John Doe")
                .withCvc("737")
                .withEndDate(CardExpiryDate.valueOf("03/30"))
                .withAddress(new Address("line1", "line2", "postcode", "city", "county", "country")).build();

        return chargeId;
    }

    private void verifyResponseForRecurringPayment(String chargeId) {
        app.givenSetup()
                .body(authCardDetails)
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId)
                .then().statusCode(200)
                .body("status", is(AUTHORISATION_SUCCESS.getValue()));

        verifyRequestToAdyenPaymentsEndpoint(chargeId);
    }

    private void verifyResponseForRecurringPaymentWith3ds(String chargeId, int statusCode, @Nullable String expectedStatus) {
        var response = app.givenSetup()
                .body(Map.of("redirect_result", REDIRECT_RESULT))
                .post(authorise3dsChargeUrlFor(chargeId))
                .then()
                .statusCode(statusCode);

        if (expectedStatus != null) {
            response.body("status", is(expectedStatus));
        }

        app.getAdyenWireMockServer()
                .verify(postRequestedFor(urlEqualTo("/payments/details"))
                        .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                        .withHeader("Idempotency-Key", equalTo("authorise3DS-" + chargeId))
                        .withRequestBody(matchingJsonPath("$.details.redirectResult", equalTo(REDIRECT_RESULT))));
    }

    private void verifyDeclineErrorResponseForRecurringPayment(String chargeId, int statusCode, String errorMessage) {
        app.givenSetup()
                .body(authCardDetails)
                .post("/v1/frontend/charges/{chargeId}/cards", chargeId)
                .then().statusCode(statusCode)
                .body("error_identifier", is("GENERIC"))
                .body("message[0]", is(errorMessage));

        verifyRequestToAdyenPaymentsEndpoint(chargeId);
    }

    private void verifyRequestToAdyenPaymentsEndpoint(String chargeId) {
        app.getAdyenWireMockServer()
                .verify(postRequestedFor(urlEqualTo("/payments"))
                        .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                        .withHeader("Idempotency-Key", equalTo("authorise-" + chargeId))
                        .withRequestBody(matchingJsonPath("$.shopperReference", equalTo(externalAgreementId)))
                        .withRequestBody(matchingJsonPath("$.storePaymentMethod", equalTo("true")))
                        .withRequestBody(matchingJsonPath("$.recurringProcessingModel", equalTo("Subscription"))));
    }

    private Optional<ChargeEntity> getAndVerifyChargeEntity(String chargeId, String pspReferenceFromAdyen) {
        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is(AUTHORISATION_SUCCESS.getValue()));
        assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));
        return charge;
    }

    private void getAndVerifyChargeEntityForFailedAuthorisation(String chargeId, String status, @Nullable String pspReferenceFromAdyen) {
        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is(status));
        assertThat(charge.get().getPaymentInstrument().isEmpty(), is(true));

        if (pspReferenceFromAdyen != null) {
            assertThat(charge.get().getGatewayTransactionId(), is(pspReferenceFromAdyen));
        }
    }

    private String setupChargeWithAgreementAndPaymentInstrument(String storedPaymentMethodId) {
        Long paymentInstrumentId = secureRandomLong();

        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                //.withFirstDigitsCardNumber(FirstDigitsCardNumber.of(first6Digits))
                //.withLastDigitsCardNumber(LastDigitsCardNumber.of(last4Digits))
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE)
                .withRecurringAuthToken(Map.of(
                        STORED_PAYMENT_METHOD_ID, storedPaymentMethodId))
                .build();
        app.getDatabaseTestHelper().addPaymentInstrument(paymentInstrumentParams);

        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(testBaseExtension.getAccountId())
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .withPaymentInstrumentId(paymentInstrumentId)
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT
        ));

        String chargeId = testBaseExtension.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(SC_CREATED)
                .body(JSON_AGREEMENT_ID_KEY, Is.is(JSON_VALID_AGREEMENT_ID_VALUE))
                .contentType(JSON)
                .extract().path("charge_id");

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_USER_NOT_PRESENT_QUEUED.getValue());
        testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_STARTED.getStatus());
        return chargeId;
    }

    private static String getStoredPaymentMethodId(Optional<ChargeEntity> charge) {
        return charge.get().getPaymentInstrument().orElseThrow().getRecurringAuthToken().orElseThrow().get("storedPaymentMethodId");
    }
}
