package uk.gov.pay.connector.queue.tasks.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenWebhookTaskHandler;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_CANCELLATION_FAILED_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_CANCELLATION_SUCCESS_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_CAPTURE_SUCCESS_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_REFUND_FAILURE_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_REFUND_SUCCESS_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

class AdyenWebhookTaskHandlerForPaymentWebhooksIT {

    public static final String CHARGE_EXTERNAL_ID = "charge-external-id";

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    private AdyenWebhookTaskHandler adyenWebhookTaskHandler;

    @BeforeEach
    void setUp() {
        adyenWebhookTaskHandler = app.getInstanceFromGuiceContainer(AdyenWebhookTaskHandler.class);
    }

    @ParameterizedTest
    @CsvSource({"CAPTURE_SUBMITTED", "CAPTURE_APPROVED_RETRY", "CAPTURED"})
    void should_update_charge_in_different_capture_state_to_CAPTURED_for_successful_capture_notification(ChargeStatus currentStatus) {
        var testCharge = createTestChargeWithStatus(currentStatus);

        adyenWebhookTaskHandler.processAdyenWebhookNotification(load(ADYEN_CAPTURE_SUCCESS_NOTIFICATION));

        var chargeFromDatabase = app.getDatabaseTestHelper()
                .getChargeByExternalId(testCharge.getExternalChargeId());
        assertThat(chargeFromDatabase.get("status"), is(CAPTURED.name()));
    }

    @ParameterizedTest
    @CsvSource({"USER_CANCEL_SUBMITTED,USER CANCELLED", "USER_CANCEL_ERROR,USER CANCELLED", "SYSTEM_CANCEL_SUBMITTED,SYSTEM CANCELLED"})
    void should_update_charge_in_valid_cancelled_state_for_successful_capture_notification(ChargeStatus currentStatus, String expectedStatus) {
        var testCharge = createTestChargeWithStatus(currentStatus);

        adyenWebhookTaskHandler.processAdyenWebhookNotification(load(ADYEN_CANCELLATION_SUCCESS_NOTIFICATION));

        var chargeFromDatabase = app.getDatabaseTestHelper()
                .getChargeByExternalId(testCharge.getExternalChargeId());
        assertThat(chargeFromDatabase.get("status"), is(expectedStatus));
    }

    @ParameterizedTest
    @CsvSource({"USER_CANCEL_SUBMITTED,USER CANCEL ERROR", "SYSTEM_CANCEL_SUBMITTED,SYSTEM CANCEL ERROR"})
    void should_update_charge_in_valid_cancelled_state_for_failed_capture_notification(ChargeStatus currentStatus, String expectedStatus) {
        var testCharge = createTestChargeWithStatus(currentStatus);

        adyenWebhookTaskHandler.processAdyenWebhookNotification(load(ADYEN_CANCELLATION_FAILED_NOTIFICATION));

        var chargeFromDatabase = app.getDatabaseTestHelper()
                .getChargeByExternalId(testCharge.getExternalChargeId());
        assertThat(chargeFromDatabase.get("status"), is(expectedStatus));
    }

    @Test
    void should_update_charge_to_REFUNDED_for_successful_refund_notification() {
        var testRefund = createRefundInSubmittedState();
        var payload = load(ADYEN_REFUND_SUCCESS_NOTIFICATION)
                .replace("{{pspReference}}", testRefund.getGatewayTransactionId())
                .replace("{{merchantReference}}", testRefund.getExternalRefundId());

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        assertRefundStatus(testRefund.getId(), REFUNDED.getValue());
    }

    @Test
    void should_update_charge_to_REFUND_ERROR_for_failed_refund_notification() {
        var testRefund = createRefundInSubmittedState();
        var payload = load(ADYEN_REFUND_FAILURE_NOTIFICATION).replace("{{pspReference}}", testRefund.getGatewayTransactionId()).replace("{{merchantReference}}", testRefund.getExternalRefundId());

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        assertRefundStatus(testRefund.getId(), REFUND_ERROR.getValue());
    }

    @Test
    void should_update_refund_in_REFUND_ERROR_state_to_REFUNDED_for_successful_refund_notification() {
        var capturedCharge = createTestChargeWithStatus(CAPTURED);
        var testRefund = app.getDatabaseFixtures()
                .aTestRefund()
                .withTestCharge(capturedCharge)
                .withGatewayTransactionId("some-pspReference-returned-from-refund-request-to-Adyen")
                .withChargeExternalId(CHARGE_EXTERNAL_ID)
                .withRefundStatus(REFUND_ERROR)
                .insert();
        var payload = load(ADYEN_REFUND_SUCCESS_NOTIFICATION)
                .replace("{{pspReference}}", testRefund.getGatewayTransactionId())
                .replace("{{merchantReference}}", testRefund.getExternalRefundId());

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        assertRefundStatus(testRefund.getId(), REFUNDED.getValue());
    }

    private static DatabaseFixtures.TestCharge createTestChargeWithStatus(ChargeStatus chargeStatus) {
        DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .withPaymentProvider(ADYEN.getName())
                .insert();

        return app.getDatabaseFixtures()
                .aTestCharge()
                .withExternalChargeId(CHARGE_EXTERNAL_ID)
                .withTransactionId("adyen-transaction-id-123")
                .withChargeStatus(chargeStatus)
                .withPaymentProvider(ADYEN.getName())
                .withTestAccount(testAccount)
                .insert();
    }

    private DatabaseFixtures.TestRefund createRefundInSubmittedState() {
        var capturedCharge = createTestChargeWithStatus(CAPTURED);
        return app.getDatabaseFixtures()
                .aTestRefund()
                .withTestCharge(capturedCharge)
                .withGatewayTransactionId("some-pspReference-returned-from-refund-request-to-Adyen")
                .withChargeExternalId(CHARGE_EXTERNAL_ID)
                .withRefundStatus(REFUND_SUBMITTED)
                .insert();
    }

    private void assertRefundStatus(Long refundId, String expectedStatus) {
        var refundFromDatabase = app.getDatabaseTestHelper().getRefund(refundId).getFirst();
        assertThat(refundFromDatabase.get("status"), is(expectedStatus));
    }
}
