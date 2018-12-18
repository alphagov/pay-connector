package uk.gov.pay.connector.gateway.processor;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class RefundNotificationProcessor {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private RefundDao refundDao;
    private UserNotificationService userNotificationService;

    @Inject
    RefundNotificationProcessor(RefundDao refundDao,
                                UserNotificationService userNotificationService) {
        this.refundDao = refundDao;
        this.userNotificationService = userNotificationService;
    }

    public void invoke(PaymentGatewayName gatewayName, RefundStatus newStatus, String reference, String transactionId) {
        if (isBlank(reference)) {
            logger.error("{} refund notification could not be used to update charge (missing reference)",
                    gatewayName);
            return;
        }

        Optional<RefundEntity> optionalRefundEntity = refundDao.findByProviderAndReference(gatewayName.getName(), reference);
        if (!optionalRefundEntity.isPresent()) {
            logger.error("{} notification '{}' could not be used to update refund (associated refund entity not found)",
                    gatewayName, reference);
            return;
        }

        RefundEntity refundEntity = optionalRefundEntity.get();
        RefundStatus oldStatus = refundEntity.getStatus();

        refundEntity.setStatus(newStatus);

        if (RefundStatus.REFUNDED.equals(newStatus)) {
            userNotificationService.sendRefundIssuedEmail(refundEntity);
        }

        GatewayAccountEntity gatewayAccount = refundEntity.getChargeEntity().getGatewayAccount();
        logger.info("Notification received for refund. Updating refund - charge_external_id={}, refund_reference={}, transaction_id={}, status={}, "
                        + "status_to={}, account_id={}, provider={}, provider_type={}",
                refundEntity.getChargeEntity().getExternalId(),
                reference,
                transactionId,
                oldStatus,
                newStatus,
                gatewayAccount.getId(),
                gatewayAccount.getGatewayName(),
                gatewayAccount.getType());
    }
}
