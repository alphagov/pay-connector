package uk.gov.pay.connector.gateway.processor;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.ChargeRefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class RefundNotificationProcessor {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private ChargeRefundService refundService;
    private UserNotificationService userNotificationService;

    @Inject
    RefundNotificationProcessor(ChargeRefundService refundService,
                                UserNotificationService userNotificationService) {
        this.refundService = refundService;
        this.userNotificationService = userNotificationService;
    }

    public void invoke(PaymentGatewayName gatewayName, RefundStatus newStatus, GatewayAccountEntity gatewayAccountEntity,
                       String reference, String transactionId, ChargeEntity chargeEntity) {
        if (isBlank(reference)) {
            logger.warn("{} refund notification could not be used to update charge (missing reference)",
                    gatewayName);
            return;
        }

        Optional<RefundEntity> optionalRefundEntity = refundService.findByProviderAndReference(gatewayName.getName(), reference);
        if (optionalRefundEntity.isEmpty()) {
            logger.warn("{} notification '{}' could not be used to update refund (associated refund entity not found)",
                    gatewayName, reference);
            return;
        }

        RefundEntity refundEntity = optionalRefundEntity.get();
        RefundStatus oldStatus = refundEntity.getStatus();

        refundService.transitionRefundState(refundEntity, newStatus);

        if (RefundStatus.REFUNDED.equals(newStatus)) {
            userNotificationService.sendRefundIssuedEmail(refundEntity, Charge.from(chargeEntity), gatewayAccountEntity);
        }

        logger.info("Notification received for refund. Updating refund - charge_external_id={}, refund_reference={}, transaction_id={}, status={}, "
                        + "status_to={}, account_id={}, provider={}, provider_type={}",
                refundEntity.getChargeExternalId(),
                reference,
                transactionId,
                oldStatus,
                newStatus,
                gatewayAccountEntity.getId(),
                gatewayAccountEntity.getGatewayName(),
                gatewayAccountEntity.getType());
    }
}
