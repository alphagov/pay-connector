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

    public void invoke(PaymentGatewayName gatewayName, RefundStatus newStatus,
                       GatewayAccountEntity gatewayAccountEntity, String gatewayTransactionId,
                       String transactionId, Charge charge) {
        if (isBlank(gatewayTransactionId)) {
            logMissingRefundReference(gatewayName, gatewayAccountEntity, charge);
            return;
        }

        Optional<RefundEntity> optionalRefundEntity =
                refundService.findByChargeExternalIdAndGatewayTransactionId(charge.getExternalId(), gatewayTransactionId);

        if (optionalRefundEntity.isEmpty()) {
            handleMissingRefundByGatewayTransactionId(gatewayName, gatewayTransactionId, transactionId, charge);
            return;
        }

        processRefundNotification(gatewayName, newStatus, gatewayAccountEntity, gatewayTransactionId, transactionId, charge, optionalRefundEntity.get());
    }

    public void processRefundByExternalId(PaymentGatewayName gatewayName, RefundStatus newStatus,
                                          GatewayAccountEntity gatewayAccountEntity, String refundExternalId, Charge charge) {
        if (isBlank(refundExternalId)) {
            logMissingRefundReference(gatewayName, gatewayAccountEntity, charge);
            return;
        }

        Optional<RefundEntity> optionalRefundEntity = refundService.findRefundByExternalId(refundExternalId);
        if (optionalRefundEntity.isEmpty()) {
            logMissingRefund(gatewayName, refundExternalId, null, null, charge);
            return;
        }

        processRefundNotification(gatewayName, newStatus, gatewayAccountEntity, refundExternalId, null, charge, optionalRefundEntity.get());
    }

    private void processRefundNotification(PaymentGatewayName gatewayName, RefundStatus newStatus,
                                           GatewayAccountEntity gatewayAccountEntity, String refundReference,
                                           String transactionId, Charge charge, RefundEntity refundEntity) {
        RefundStatus currentStatus = refundEntity.getStatus();

        if (isRefundTransitionRedundant(currentStatus, newStatus)) {
            logger.info("Notification received for refund [{}] is redundant and therefore ignored because refund is already in state [{}]",
                    refundEntity.getExternalId(), currentStatus);
            return;
        }

        if (isAdyenRefundTransitionIllegal(gatewayName, currentStatus, newStatus)) {
            logAdyenIllegalRefundTransition(refundEntity, newStatus, currentStatus);
            return;
        } else if (gatewayName != ADYEN && isRefundTransitionIllegal(currentStatus, newStatus)) {
            logIllegalRefundTransition(refundEntity, newStatus, currentStatus);
            return;
        }

        refundService.transitionRefundState(refundEntity, gatewayAccountEntity, newStatus, charge);

        if (newStatus == REFUNDED) {
            userNotificationService.sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity);
        }

        String stateTransitionMessage = newStatus == REFUND_ERROR ? "Refund request record set as failed (REFUND_ERROR)" : "Refund request record set as successful (REFUNDED)";

        logger.atInfo()
                .addKeyValue(PAYMENT_EXTERNAL_ID, refundEntity.getChargeExternalId())
                .addKeyValue(REFUND_EXTERNAL_ID, refundEntity.getExternalId())
                .addKeyValue(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId())
                .addKeyValue(PROVIDER, charge.getPaymentGatewayName())
                .addKeyValue(GATEWAY_ACCOUNT_TYPE, gatewayAccountEntity.getType())
                .addKeyValue("payment_gateway_transaction_id", transactionId)
                .addKeyValue("gateway_transaction_id", refundReference)
                .addKeyValue("from_status", currentStatus)
                .addKeyValue("to_status", newStatus)
                .log("Notification received for refund. Updating refund: {}", stateTransitionMessage);

    }

    private void handleMissingRefundByGatewayTransactionId(PaymentGatewayName gatewayName, String gatewayTransactionId, String transactionId, Charge charge) {
        Optional<Refund> mayBeHistoricRefund =
                refundService.findHistoricRefundByChargeExternalIdAndGatewayTransactionId(charge, gatewayTransactionId);

        mayBeHistoricRefund.ifPresentOrElse(
                refund -> logger.atWarn()
                        .addKeyValue(REFUND_EXTERNAL_ID, refund.getExternalId())
                        .addKeyValue(PAYMENT_EXTERNAL_ID, charge.getExternalId())
                        .addKeyValue(PROVIDER, gatewayName)
                        .log("{} notification could not be processed as refund [{}] has been expunged from connector", gatewayName, refund.getExternalId()),
                () -> logMissingRefund(gatewayName, gatewayTransactionId, transactionId, gatewayTransactionId, charge)
        );
    }

    private void logMissingRefund(PaymentGatewayName gatewayName, String refundExternalId, String transactionId, String gatewayTransactionId, Charge charge) {
        logger.atWarn()
                .addKeyValue(PAYMENT_EXTERNAL_ID, charge.getExternalId())
                .addKeyValue(PROVIDER, gatewayName)
                .addKeyValue("payment_gateway_transaction_id", transactionId)
                .addKeyValue(REFUND_EXTERNAL_ID, refundExternalId)
                .addKeyValue("gateway_transaction_id", gatewayTransactionId)
                .log("{} notification '{}' could not be used to update refund (associated refund entity not found) for charge [{}]",
                        gatewayName, refundExternalId, charge.getExternalId());
    }

    private void logMissingRefundReference(PaymentGatewayName gatewayName, GatewayAccountEntity gatewayAccountEntity, Charge charge) {
        logger.atWarn()
                .setMessage("Refund notification could not be used to update charge (missing reference)")
                .addKeyValue(PAYMENT_EXTERNAL_ID, charge.getExternalId())
                .addKeyValue(PROVIDER, gatewayName)
                .addKeyValue(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId())
                .log();
    }


    private boolean isRefundTransitionRedundant(RefundStatus currentStatus, RefundStatus newStatus) {
        return newStatus == currentStatus;
    }

    private boolean isRefundTransitionIllegal(RefundStatus currentStatus, RefundStatus newStatus) {
        return (currentStatus == REFUNDED && newStatus == REFUND_ERROR) || (currentStatus == REFUND_ERROR && newStatus == REFUNDED);
    }

    private boolean isAdyenRefundTransitionIllegal(PaymentGatewayName gatewayName, RefundStatus currentStatus, RefundStatus newStatus) {
        return gatewayName == ADYEN && currentStatus == REFUNDED && newStatus == REFUND_ERROR;
    }

    private void logIllegalRefundTransition(RefundEntity refundEntity, RefundStatus newStatus, RefundStatus currentStatus) {
        logger.info("Notification received for refund would cause an illegal state transition: refund [{}] cannot be set as [{}] because it is already in state [{}].",
                refundEntity.getExternalId(), newStatus, currentStatus);
    }

    private void logAdyenIllegalRefundTransition(RefundEntity refundEntity, RefundStatus newStatus, RefundStatus currentStatus) {
        logger.error("Adyen Notification received for refund would cause an illegal state transition: refund [{}] cannot be set as [{}] because it is already in state [{}].",
                refundEntity.getExternalId(), newStatus, currentStatus);
    }
}
