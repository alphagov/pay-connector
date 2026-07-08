package uk.gov.pay.connector.gateway.processor;

import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.event.Level;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
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
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultGatewayAccountEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

@ExtendWith(MockitoExtension.class)
class AdyenRefundNotificationProcessorTest {

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(RefundNotificationProcessor.class);

    @Mock
    private RefundService refundService;
    @Mock
    private UserNotificationService userNotificationService;

    private RefundNotificationProcessor refundNotificationProcessor;
    private RefundEntity refundEntity;

    private static final String REFUND_GATEWAY_TRANSACTION_ID = "refund-gateway-tx-id";
    private static final String TRANSACTION_ID = "transactionId";
    private final GatewayAccountEntity gatewayAccountEntity = defaultGatewayAccountEntity();
    private final ChargeEntity chargeEntity = aValidChargeEntity()
            .withGatewayAccountEntity(gatewayAccountEntity)
            .withReference(ServicePaymentReference.of("payment-reference"))
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
    void shouldTransitionRefund_WhenRefundStatusWasSetAsRefundError() {
        refundEntity.setStatus(RefundStatus.REFUND_ERROR);
        when(refundService.findByChargeExternalIdAndGatewayTransactionId(charge.getExternalId(), REFUND_GATEWAY_TRANSACTION_ID))
                .thenReturn(Optional.of(refundEntity));

        refundNotificationProcessor.invoke(
                ADYEN,
                RefundStatus.REFUNDED,
                gatewayAccountEntity,
                REFUND_GATEWAY_TRANSACTION_ID,
                TRANSACTION_ID,
                charge);

        verify(refundService)
                .transitionRefundStateForAdyenWebhook(refundEntity, gatewayAccountEntity, RefundStatus.REFUNDED, charge);
        verify(userNotificationService).sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity);
    }

    @Test
    void shouldIgnoreDuplicateRefundErrorWebhookForAdyen() {
        refundEntity.setStatus(RefundStatus.REFUND_ERROR);
        when(refundService.findByChargeExternalIdAndGatewayTransactionId(charge.getExternalId(), REFUND_GATEWAY_TRANSACTION_ID))
                .thenReturn(Optional.of(refundEntity));

        refundNotificationProcessor.invoke(
                ADYEN,
                RefundStatus.REFUND_ERROR,
                gatewayAccountEntity,
                REFUND_GATEWAY_TRANSACTION_ID,
                TRANSACTION_ID,
                charge);

        then(refundService)
                .should(never())
                .transitionRefundState(any(), any(), any(), any());
        then(refundService)
                .should(never())
                .transitionRefundStateForAdyenWebhook(any(), any(), any(), any());
        then(userNotificationService)
                .should(never())
                .sendRefundIssuedEmail(any(), any(), any());
        assertThat(logs.getEvents(), everyItem(hasProperty("level", is(Level.INFO))));
        logs.assertContains("Notification received for refund [someExternalId] is redundant and therefore ignored because refund is already in state [REFUND ERROR]");
    }

    @Test
    void shouldLogIllegalStateTransitionAtErrorLevel_IfRefundFailedWhenRefundStatusWasSetAsRefunded() {
        refundEntity.setStatus(RefundStatus.REFUNDED);
        when(refundService.findByChargeExternalIdAndGatewayTransactionId(charge.getExternalId(), REFUND_GATEWAY_TRANSACTION_ID))
                .thenReturn(Optional.of(refundEntity));

        refundNotificationProcessor.invoke(
                ADYEN,
                RefundStatus.REFUND_ERROR,
                gatewayAccountEntity,
                REFUND_GATEWAY_TRANSACTION_ID,
                TRANSACTION_ID,
                charge);

        assertThat(logs.getEvents(), everyItem(hasProperty("level", is(Level.ERROR))));
        logs.assertContains("Adyen Notification received for refund would cause an illegal state transition");
        then(refundService)
                .should(never())
                .transitionRefundState(any(), any(), any(), any());
        then(userNotificationService)
                .should(never())
                .sendRefundIssuedEmail(any(), any(), any());
    }
}
