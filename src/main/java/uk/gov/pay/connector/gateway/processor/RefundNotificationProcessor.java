package uk.gov.pay.connector.gateway.processor;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;
import static uk.gov.service.payments.logging.LoggingKeys.REFUND_EXTERNAL_ID;

public class RefundNotificationProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final RefundService refundService;
    private final UserNotificationService userNotificationService;

    @Inject
    public RefundNotificationProcessor(RefundService refundService,
                                UserNotificationService userNotificationService) {
        this.refundService = refundService;
        this.userNotificationService = userNotificationService;
    }

    public void invoke(PaymentGatewayName gatewayName, RefundStatus newStatus, GatewayAccountEntity gatewayAccountEntity,
                       String gatewayTransactionId, String transactionId, Charge charge) {
        if (isBlank(gatewayTransactionId)) {
            logger.warn("Refund notification could not be used to update charge (missing reference)",
                    kv(PAYMENT_EXTERNAL_ID, charge.getExternalId()),
                    kv(PROVIDER, gatewayName),
                    kv(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId()));
            return;
        }

        Optional<RefundEntity> optionalRefundEntity =
                refundService.findByChargeExternalIdAndGatewayTransactionId(charge.getExternalId(), gatewayTransactionId);

        if (optionalRefundEntity.isEmpty()) {
            Optional<Refund> mayBeHistoricRefund
                    = refundService.findHistoricRefundByChargeExternalIdAndGatewayTransactionId(charge, gatewayTransactionId);

            mayBeHistoricRefund.ifPresentOrElse(
                    refund -> logger.warn("{} notification could not be processed as refund [{}] has been expunged from connector",
                            gatewayName, refund.getExternalId(),
                            kv(REFUND_EXTERNAL_ID, refund.getExternalId()), kv(PAYMENT_EXTERNAL_ID, charge.getExternalId()),
                            kv(PROVIDER, gatewayName)),
                    () -> logger.warn("{} notification '{}' could not be used to update refund (associated refund entity not found) for charge [{}]",
                            gatewayName, gatewayTransactionId, charge.getExternalId(),
                            kv(PAYMENT_EXTERNAL_ID, charge.getExternalId()), kv(PROVIDER, gatewayName),
                            kv("payment_gateway_transaction_id", transactionId),
                            kv("gateway_transaction_id", gatewayTransactionId))
            );
            return;
        }

        RefundEntity refundEntity = optionalRefundEntity.get();
        RefundStatus oldStatus = refundEntity.getStatus();

        if (isRefundTransitionRedundant(oldStatus, newStatus)) {
            logger.info("Notification received for refund [{}] is redundant and therefore ignored because refund is already in state [{}]",
                    refundEntity.getExternalId(), oldStatus);
            return;
        }

        boolean isAdyenRefundErrorToRefundedTransition = isAdyenRefundErrorToRefundedTransition(gatewayName, oldStatus, newStatus);

        if (isRefundTransitionIllegal(oldStatus, newStatus) && !isAdyenRefundErrorToRefundedTransition) {
            logIllegalRefundTransition(refundEntity, gatewayName, newStatus, oldStatus);
            return;
        }

        transitionRefundState(refundEntity, gatewayAccountEntity, newStatus, charge, isAdyenRefundErrorToRefundedTransition);

        if (newStatus == REFUNDED) {
            userNotificationService.sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity);
        }

        String stateTransitionMessage = newStatus == REFUND_ERROR ? "Refund request record set as failed (REFUND_ERROR)" : "Refund request record set as successful (REFUNDED)";

        logger.info("Notification received for refund. Updating refund: {}",
                stateTransitionMessage,
                kv(PAYMENT_EXTERNAL_ID, refundEntity.getChargeExternalId()),
                kv(REFUND_EXTERNAL_ID, refundEntity.getExternalId()),
                kv(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId()),
                kv(PROVIDER, charge.getPaymentGatewayName()),
                kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountEntity.getType()),
                kv("payment_gateway_transaction_id", transactionId),
                kv("gateway_transaction_id", gatewayTransactionId),
                kv("from_status", oldStatus),
                kv("to_status", newStatus)
        );
    }

    private boolean isRefundTransitionRedundant(RefundStatus oldStatus, RefundStatus newStatus) {
        return newStatus == oldStatus;
    }

    private boolean isRefundTransitionIllegal(RefundStatus oldStatus, RefundStatus newStatus) {
        return (oldStatus == REFUNDED && newStatus == REFUND_ERROR) || (oldStatus == REFUND_ERROR && newStatus == REFUNDED);
    }

    private boolean isAdyenRefundErrorToRefundedTransition(PaymentGatewayName gatewayName, RefundStatus oldStatus, RefundStatus newStatus) {
        return gatewayName == ADYEN && oldStatus == REFUND_ERROR && newStatus == REFUNDED;
    }

    private void transitionRefundState(RefundEntity refundEntity, GatewayAccountEntity gatewayAccountEntity,
                                       RefundStatus newStatus, Charge charge, boolean isAdyenRefundErrorToRefundedTransition) {
        if (isAdyenRefundErrorToRefundedTransition) {
            refundService.transitionRefundStateForAdyenWebhook(refundEntity, gatewayAccountEntity, newStatus, charge);
            return;
        }
        refundService.transitionRefundState(refundEntity, gatewayAccountEntity, newStatus, charge);
    }

    private void logIllegalRefundTransition(RefundEntity refundEntity, PaymentGatewayName gatewayName,
                                            RefundStatus newStatus, RefundStatus oldStatus) {
        String logMessage = String.format("Notification received for refund would cause an illegal state transition: refund [%s] cannot be set as [%s] because it is already in state [%s].",
                refundEntity.getExternalId(), newStatus, oldStatus);
        if (gatewayName == ADYEN) {
            logger.error("Adyen {}", logMessage);
            return;
        }
        logger.info(logMessage);
    }
}
