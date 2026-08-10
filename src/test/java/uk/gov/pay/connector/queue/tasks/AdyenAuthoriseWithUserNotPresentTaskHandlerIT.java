package uk.gov.pay.connector.queue.tasks;

import io.github.netmikey.logunit.api.LogCapturer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.pay.connector.queue.tasks.handlers.AuthoriseWithUserNotPresentHandler;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_QUEUED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_ERROR_GATEWAY;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_FAILED_REJECTED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.AMOUNT;
import static uk.gov.pay.connector.it.util.RecurringPaymentSetupUtil.JSON_VALID_AGREEMENT_ID_VALUE;
import static uk.gov.pay.connector.it.util.RecurringPaymentSetupUtil.setupChargeWithAgreementAndPaymentInstrument;

public class AdyenAuthoriseWithUserNotPresentTaskHandlerIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("adyen", app.getLocalPort(), app.getDatabaseTestHelper());
    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(CaptureQueue.class);

    private ChargeDao chargeDao;

    private static final String PSP_REFERENCE_FROM_ADYEN = "993617895215577D";

    @BeforeEach
    void setUp() {
        app.getDatabaseTestHelper().enableRecurring(Long.parseLong(testBaseExtension.getAccountId()));
        chargeDao = app.getInstanceFromGuiceContainer(ChargeDao.class);
    }

    @Test
    void shouldProcess_ACorrectlyConfiguredAuthorisationModeAgreementCharge_AndMarkForCapture() {
        AuthoriseWithUserNotPresentHandler taskHandler = app.getInstanceFromGuiceContainer(AuthoriseWithUserNotPresentHandler.class);
        String storedPaymentMethodId = "4242";
        var chargeId = setupChargeWithAgreementAndPaymentInstrument(testBaseExtension, app, storedPaymentMethodId);
        var expectedStatus = CAPTURE_QUEUED.getValue();

        app.getAdyenCheckoutMockClient().mockAuthorisationSuccessForRecurringPayment(PSP_REFERENCE_FROM_ADYEN, storedPaymentMethodId);

        taskHandler.process(chargeId);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, expectedStatus);
        testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());

        var charge = getChargeEntity(chargeId);
        verifyChargeEntity(charge, storedPaymentMethodId, expectedStatus);
        logs.assertContains("Charge [" + chargeId + "] added to capture queue.");
    }

    @Test
    void shouldProcess_AnAuthorisationDeclineResponse_AndMarkAsRejectedWithReason() {
        AuthoriseWithUserNotPresentHandler taskHandler = app.getInstanceFromGuiceContainer(AuthoriseWithUserNotPresentHandler.class);
        String storedPaymentMethodId = "4242";
        var chargeId = setupChargeWithAgreementAndPaymentInstrument(testBaseExtension, app, storedPaymentMethodId);
        var expectedStatus = AUTHORISATION_REJECTED.getValue();
        var expectedReason = "Expired Card";
        var expectedReasonCode = "6";

        app.getAdyenCheckoutMockClient().mockAuthorisationRejectedForRecurringPayment(PSP_REFERENCE_FROM_ADYEN,
                storedPaymentMethodId, expectedReason, expectedReasonCode);

        taskHandler.process(chargeId);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, expectedStatus);
        testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_FAILED_REJECTED.getStatus());

        var charge = getChargeEntity(chargeId);

        assertThat(charge.getPaymentInstrument().isPresent(), is(true));
        verifyRequestToAdyenPaymentsEndpoint(chargeId, storedPaymentMethodId, charge.getPaymentInstrument().get().getChargeExternalId());
        verifyChargeEntity(charge, storedPaymentMethodId, expectedStatus);
        assertThat(charge.getGatewayRejectionReason(), Matchers.is(expectedReasonCode + " - " + expectedReason));
    }

    @Test
    void shouldProcess_AnAuthorisationErrorResponse_AndMarkAsError() {
        AuthoriseWithUserNotPresentHandler taskHandler = app.getInstanceFromGuiceContainer(AuthoriseWithUserNotPresentHandler.class);
        String storedPaymentMethodId = "4242";
        var chargeId = setupChargeWithAgreementAndPaymentInstrument(testBaseExtension, app, storedPaymentMethodId);
        var expectedStatus = AUTHORISATION_ERROR.getValue();

        app.getAdyenCheckoutMockClient().mockAuthorisationErrorForRecurringPayment(PSP_REFERENCE_FROM_ADYEN,
                storedPaymentMethodId);

        taskHandler.process(chargeId);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, expectedStatus);
        testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_ERROR_GATEWAY.getStatus());

        var charge = getChargeEntity(chargeId);

        assertThat(charge.getPaymentInstrument().isPresent(), is(true));
        verifyRequestToAdyenPaymentsEndpoint(chargeId, storedPaymentMethodId, charge.getPaymentInstrument().get().getChargeExternalId());
        verifyChargeEntity(charge, storedPaymentMethodId, expectedStatus);
    }

    @Test
    void shouldProcess_AGatewayError_AndMarkAsAuthorisationError() {
        AuthoriseWithUserNotPresentHandler taskHandler = app.getInstanceFromGuiceContainer(AuthoriseWithUserNotPresentHandler.class);
        String storedPaymentMethodId = "4242";
        var chargeId = setupChargeWithAgreementAndPaymentInstrument(testBaseExtension, app, storedPaymentMethodId);
        var expectedStatus = AUTHORISATION_UNEXPECTED_ERROR.getValue();

        app.getAdyenCheckoutMockClient().mockAuthorisationClientError();

        taskHandler.process(chargeId);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, expectedStatus);
        testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_ERROR_GATEWAY.getStatus());

        var charge = getChargeEntity(chargeId);
        
        assertThat(charge.getPaymentInstrument().isPresent(), is(true));
        verifyRequestToAdyenPaymentsEndpoint(chargeId, storedPaymentMethodId, charge.getPaymentInstrument().get().getChargeExternalId());
        assertThat(charge.getStatus(), is(expectedStatus));
    }

    private void verifyRequestToAdyenPaymentsEndpoint(String chargeId, String storedPaymentMethodId, String chargeExternalId) {
        app.getAdyenWireMockServer()
                .verify(postRequestedFor(urlEqualTo("/payments"))
                        .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                        .withHeader("Idempotency-Key", equalTo("authorise-" + chargeId))
                        .withRequestBody(matchingJsonPath("$.shopperReference", equalTo(JSON_VALID_AGREEMENT_ID_VALUE + "-" + chargeExternalId)))
                        .withRequestBody(matchingJsonPath("$.recurringProcessingModel", equalTo("UnscheduledCardOnFile")))
                        .withRequestBody(matchingJsonPath("$.paymentMethod.storedPaymentMethodId", equalTo(storedPaymentMethodId)))
                        .withRequestBody(matchingJsonPath("$.paymentMethod.type", equalTo("scheme")))
                        .withRequestBody(matchingJsonPath("$.amount.value", equalTo(String.valueOf(AMOUNT))))
                        .withRequestBody(matchingJsonPath("$.shopperInteraction", equalTo("ContAuth")))
                        .withRequestBody(matchingJsonPath("$.reference", equalTo(chargeId)))
                        .withRequestBody(matchingJsonPath("$.store", equalTo("test-store-id")))
                        .withRequestBody(matchingJsonPath("$.merchantAccount", equalTo("adyen-test-merchant-account-id")))
                        .withRequestBody(matchingJsonPath("$.additionalData.manualCapture", equalTo("true"))));
    }

    private ChargeEntity getChargeEntity(String chargeId) {
        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        return charge.get();
    }

    private void verifyChargeEntity(ChargeEntity charge, String storedPaymentMethodId, String expectedStatus) {
        assertThat(charge.getStatus(), is(expectedStatus));
        assertThat(charge.getGatewayTransactionId(), is(PSP_REFERENCE_FROM_ADYEN));

        assertThat(charge.getPaymentInstrument().isPresent(), is(true));
        assertThat(charge.getPaymentInstrument().get().getRecurringAuthToken().isPresent(), is(true));
        assertThat(charge.getPaymentInstrument().get().getRecurringAuthToken().get().get("storedPaymentMethodId"), is(storedPaymentMethodId));
    }
}
