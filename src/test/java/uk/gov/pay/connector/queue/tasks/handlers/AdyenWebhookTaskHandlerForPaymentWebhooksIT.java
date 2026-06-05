package uk.gov.pay.connector.queue.tasks.handlers;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_CAPTURE_SUCCESS_NOTIFICATION;

class AdyenWebhookTaskHandlerForPaymentWebhooksIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @ParameterizedTest
    @MethodSource("chargeStatusAndExpectedTargetStatus")
    void shouldUpdateChargeInDifferentCaptureStateToCapture_ForSuccessfulCaptureNotification(
            ChargeStatus currentStatus, ChargeStatus expectedTargetStatus) {
        DatabaseFixtures.TestCharge testCharge = getTestChargeWithStatus(currentStatus);
        AdyenWebhookTaskHandler adyenWebhookTaskHandler = app.getInstanceFromGuiceContainer(AdyenWebhookTaskHandler.class);
        String captureNotification = TestTemplateResourceLoader.load(ADYEN_CAPTURE_SUCCESS_NOTIFICATION);

        adyenWebhookTaskHandler.processAdyenWebhookNotification(captureNotification);

        Map<String, Object> chargeFromDatabase = app.getDatabaseTestHelper().getChargeByExternalId(testCharge.getExternalChargeId());
        assertThat(chargeFromDatabase.get("status"), is(expectedTargetStatus.name()));
    }

    static Stream<Arguments> chargeStatusAndExpectedTargetStatus() {
        return Stream.of(
                Arguments.of(CAPTURE_SUBMITTED, CAPTURED),
                Arguments.of(CAPTURE_APPROVED_RETRY, CAPTURED),
                Arguments.of(CAPTURED, CAPTURED)
        );
    }

    private static DatabaseFixtures.TestCharge getTestChargeWithStatus(ChargeStatus chargeStatus) {
        DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .withPaymentProvider(ADYEN.getName())
                .insert();

        return app.getDatabaseFixtures()
                .aTestCharge()
                .withTransactionId("adyen-transaction-id-123")
                .withChargeStatus(chargeStatus)
                .withPaymentProvider(ADYEN.getName())
                .withTestAccount(testAccount)
                .insert();
    }

}
