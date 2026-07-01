package uk.gov.pay.connector.gateway.processor;

import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.event.Level;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

@ExtendWith(MockitoExtension.class)
class RefundNotificationProcessorTest {

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(RefundNotificationProcessor.class);

    @Mock
    private RefundService refundService;
    @Mock
    private UserNotificationService userNotificationService;

    RefundNotificationProcessor refundNotificationProcessor;
    RefundEntity refundEntity;

    private static final PaymentGatewayName paymentGatewayName = PaymentGatewayName.WORLDPAY;
    private static final String PAYMENT_REFERENCE = "payment-reference";
    private static final String REFUND_GATEWAY_TRANSACTION_ID = "refund-gateway-tx-id";
    private static final String TRANSACTION_ID = "transactionId";
    private final GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
    private final ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
            .withGatewayAccountEntity(gatewayAccountEntity)
            .withReference(ServicePaymentReference.of(PAYMENT_REFERENCE))
            .withTransactionId(TRANSACTION_ID)
            .build();
    private Charge charge;

    @BeforeEach
    void setup() {
        charge = Charge.from(chargeEntity);
        refundEntity = aValidRefundEntity().build();

        refundNotificationProcessor = new RefundNotificationProcessor(refundService, userNotificationService);
    }

    @Test
    void shouldInvokeTransitionRefundStateForSuccessfulRefund() {
        var targetRefundStatus = RefundStatus.REFUNDED;
        when(refundService.findByChargeExternalIdAndGatewayTransactionId(
                charge.getExternalId(),
                REFUND_GATEWAY_TRANSACTION_ID))
                .thenReturn(Optional.of(refundEntity));

        invokeRefundNotificationProcessorWithNewStatus(targetRefundStatus);

        verify(refundService)
                .transitionRefundState(refundEntity, gatewayAccountEntity, targetRefundStatus, charge);
    }

    @Test
    void shouldInvokeSendEmailNotificationsForSuccessfulRefunds() {
        Optional<RefundEntity> optionalRefundEntity = Optional.of(refundEntity);
        when(refundService.findByChargeExternalIdAndGatewayTransactionId(charge.getExternalId(), REFUND_GATEWAY_TRANSACTION_ID)).thenReturn(optionalRefundEntity);

        invokeRefundNotificationProcessorWithNewStatus(RefundStatus.REFUNDED);
        verify(userNotificationService).sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity);
    }

    @Test
    void shouldNotInvokeSendEmailNotifications_WhenRefundStatusIsNotRefunded() {
        invokeRefundNotificationProcessorWithNewStatus(RefundStatus.REFUND_ERROR);
        verify(userNotificationService, never()).sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity);
    }

    @Test
    void shouldNotInvokeSendEmailNotifications_WhenRefundStatusWasSetAsRefundError() {
        refundEntity.setStatus(RefundStatus.REFUND_ERROR);
        Optional<RefundEntity> optionalRefundEntity = Optional.of(refundEntity);
        when(refundService.findByChargeExternalIdAndGatewayTransactionId(charge.getExternalId(), REFUND_GATEWAY_TRANSACTION_ID)).thenReturn(optionalRefundEntity);

        invokeRefundNotificationProcessorWithNewStatus(RefundStatus.REFUNDED);
        verify(userNotificationService, never()).sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity);
    }

    @Test
    void shouldLogFailedRefund_WhenRefundStatusWasSetAsRefundError() {
        Optional<RefundEntity> optionalRefundEntity = Optional.of(refundEntity);
        when(refundService.findByChargeExternalIdAndGatewayTransactionId(charge.getExternalId(), REFUND_GATEWAY_TRANSACTION_ID)).thenReturn(optionalRefundEntity);

        invokeRefundNotificationProcessorWithNewStatus(RefundStatus.REFUND_ERROR);

        logs.assertContains("Refund request record set as failed (REFUND_ERROR)");
    }

    @Test
    void shouldLogIllegalStateTransition_IfRefundedWhenRefundStatusWasSetAsRefundError() {
        refundEntity.setStatus(RefundStatus.REFUND_ERROR);
        Optional<RefundEntity> optionalRefundEntity = Optional.of(refundEntity);
        when(refundService.findByChargeExternalIdAndGatewayTransactionId(charge.getExternalId(), REFUND_GATEWAY_TRANSACTION_ID)).thenReturn(optionalRefundEntity);

        invokeRefundNotificationProcessorWithNewStatus(RefundStatus.REFUNDED);

        logs.assertContains("Notification received for refund would cause an illegal state transition");
    }

    @Test
    void shouldLogIllegalStateTransition_IfRefundFailedWhenRefundStatusWasSetAsRefunded() {
        refundEntity.setStatus(RefundStatus.REFUNDED);
        Optional<RefundEntity> optionalRefundEntity = Optional.of(refundEntity);
        when(refundService.findByChargeExternalIdAndGatewayTransactionId(charge.getExternalId(), REFUND_GATEWAY_TRANSACTION_ID)).thenReturn(optionalRefundEntity);

        invokeRefundNotificationProcessorWithNewStatus(RefundStatus.REFUND_ERROR);

        logs.assertContains("Notification received for refund would cause an illegal state transition");
    }

    @Test
    void shouldLogError_whenRefundGatewayTransactionIdIsNotAvailable() {
        refundNotificationProcessor.invoke(paymentGatewayName, RefundStatus.REFUND_ERROR, gatewayAccountEntity, null, TRANSACTION_ID, charge);

        logs.assertContains("Refund notification could not be used to update charge (missing reference)");
    }

    @Test
    void shouldLogError_whenRefundEntityIsNotAvailable() {
        refundNotificationProcessor.invoke(paymentGatewayName, RefundStatus.REFUNDED, gatewayAccountEntity, "unknown", TRANSACTION_ID, charge);

        String expectedLogMessage = String.format("%s notification '%s' could not be used to update refund (associated refund entity not found) for charge [%s]",
                paymentGatewayName,
                "unknown",
                charge.getExternalId());
        logs.assertContains(expectedLogMessage);
    }

    @Test
    void shouldLogWarning_whenNotificationIsForAnExpungedRefund() {
        String gatewayTransactionId = "refund-gateway-tx-id123";
        when(refundService.findHistoricRefundByChargeExternalIdAndGatewayTransactionId(charge, gatewayTransactionId))
                .thenReturn(Optional.of(Refund.from(refundEntity)));

        refundNotificationProcessor.invoke(paymentGatewayName, RefundStatus.REFUNDED, gatewayAccountEntity, gatewayTransactionId, TRANSACTION_ID, charge);

        String expectedLogMessage = String.format("%s notification could not be processed as refund [%s] has been expunged from connector",
                paymentGatewayName, refundEntity.getExternalId());
        logs.assertContains(expectedLogMessage);
    }

    @Nested
    @ParameterizedClass
    @EnumSource(RefundStatus.class)
    class WhenOldStatusIsTheSameAsTheNewStatus {

        @Parameter
        RefundStatus status;

        @BeforeEach
        void setUp() {
            refundEntity.setStatus(status);
            when(refundService.findByChargeExternalIdAndGatewayTransactionId(charge.getExternalId(), REFUND_GATEWAY_TRANSACTION_ID))
                    .thenReturn(Optional.of(refundEntity));
        }

        @Test
        void shouldNotTransitionTheRefundState() {
            var newStatus = status;
            invokeRefundNotificationProcessorWithNewStatus(newStatus);

            then(refundService)
                    .should(never())
                    .transitionRefundState(any(), any(), any(), any());
        }

        @Test
        void shouldNotSendRefundIssuedEmail() {
            invokeRefundNotificationProcessorWithNewStatus(status);

            then(userNotificationService)
                    .should(never())
                    .sendRefundIssuedEmail(any(), any(), any());
        }

        @Test
        void shouldLogRedundantNotificationMessageAtInfoLevel() {
            invokeRefundNotificationProcessorWithNewStatus(status);

            assertThat(logs.getEvents(), everyItem(hasProperty("level", is(Level.INFO))));
            logs.assertContains("Notification received for refund [someExternalId] is redundant and " +
                    "therefore ignored because refund is already in state [%s]".formatted(status));
        }
    }

    private void invokeRefundNotificationProcessorWithNewStatus(RefundStatus newStatus) {
        refundNotificationProcessor.invoke(
                paymentGatewayName,
                newStatus,
                gatewayAccountEntity,
                REFUND_GATEWAY_TRANSACTION_ID,
                TRANSACTION_ID,
                charge);
    }
}
