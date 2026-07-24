package uk.gov.pay.connector.queue.tasks;

import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.pay.connector.queue.tasks.handlers.AuthoriseWithUserNotPresentHandler;
import uk.gov.pay.connector.util.AddAgreementParams;
import uk.gov.pay.connector.util.AddPaymentInstrumentParams;

import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.http.ContentType.JSON;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_USER_NOT_PRESENT_QUEUED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_QUEUED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory.STORED_PAYMENT_METHOD_ID;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.AMOUNT;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AMOUNT_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AUTH_MODE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_VALUE;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_VALUE;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;

public class AdyenAuthoriseWithUserNotPresentTaskHandlerIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("adyen", app.getLocalPort(), app.getDatabaseTestHelper());
    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(CaptureQueue.class);

    private ChargeDao chargeDao;

    private static final String JSON_AGREEMENT_ID_KEY = "agreement_id";
    private static final String JSON_VALID_AGREEMENT_ID_VALUE = "12345678901234567890123456";
    private static final String JSON_AUTH_MODE_AGREEMENT = "agreement";
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
        var chargeId = setupChargeWithAgreementAndPaymentInstrument(storedPaymentMethodId);

        app.getAdyenCheckoutMockClient().mockAuthorisationSuccessForRecurringPayment(PSP_REFERENCE_FROM_ADYEN, storedPaymentMethodId);

        taskHandler.process(chargeId);

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, CAPTURE_QUEUED.getValue());
        testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());

        verifyRequestToAdyenPaymentsEndpoint(chargeId, storedPaymentMethodId);

        getAndVerifyChargeEntity(chargeId, storedPaymentMethodId);

        logs.assertContains("Charge [" + chargeId + "] added to capture queue.");
    }

    private String setupChargeWithAgreementAndPaymentInstrument(String storedPaymentMethodId) {
        Long paymentInstrumentId = secureRandomLong();

        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
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
                .body(JSON_AGREEMENT_ID_KEY, is(JSON_VALID_AGREEMENT_ID_VALUE))
                .contentType(JSON)
                .extract().path("charge_id");

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_USER_NOT_PRESENT_QUEUED.getValue());
        testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_STARTED.getStatus());
        return chargeId;
    }

    private void verifyRequestToAdyenPaymentsEndpoint(String chargeId, String storedPaymentMethodId) {
        app.getAdyenWireMockServer()
                .verify(postRequestedFor(urlEqualTo("/payments"))
                        .withHeader("X-API-Key", equalTo("adyen-test-company-api-key"))
                        .withHeader("Idempotency-Key", equalTo("authorise-" + chargeId))
                        .withRequestBody(matchingJsonPath("$.shopperReference", equalTo(JSON_VALID_AGREEMENT_ID_VALUE)))
                        .withRequestBody(matchingJsonPath("$.recurringProcessingModel", equalTo("Subscription")))
                        .withRequestBody(matchingJsonPath("$.paymentMethod.storedPaymentMethodId", equalTo(storedPaymentMethodId)))
                        .withRequestBody(matchingJsonPath("$.amount.value", equalTo(String.valueOf(AMOUNT))))
                        .withRequestBody(matchingJsonPath("$.shopperInteraction", equalTo("ContAuth")))
                        .withRequestBody(matchingJsonPath("$.reference", equalTo(chargeId)))
                        .withRequestBody(matchingJsonPath("$.store", equalTo("test-store-id")))
                        .withRequestBody(matchingJsonPath("$.merchantAccount", equalTo("adyen-test-merchant-account-id")))
                        .withRequestBody(matchingJsonPath("$.additionalData.manualCapture", equalTo("true"))));
    }

    private void getAndVerifyChargeEntity(String chargeId, String storedPaymentMethodId) {
        Optional<ChargeEntity> charge = chargeDao.findByExternalId(chargeId);
        assertThat(charge.isPresent(), is(true));
        assertThat(charge.get().getStatus(), is(CAPTURE_QUEUED.getValue()));
        assertThat(charge.get().getGatewayTransactionId(), is(PSP_REFERENCE_FROM_ADYEN));

        assertThat(charge.get().getPaymentInstrument().isPresent(), is(true));
        assertThat(charge.get().getPaymentInstrument().get().getRecurringAuthToken().isPresent(), is(true));
        assertThat(charge.get().getPaymentInstrument().get().getRecurringAuthToken().get().get("storedPaymentMethodId"), is(storedPaymentMethodId));
    }
}
