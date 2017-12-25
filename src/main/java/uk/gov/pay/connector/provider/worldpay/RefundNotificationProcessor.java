package uk.gov.pay.connector.provider.worldpay;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.PaymentGatewayName;
import uk.gov.pay.connector.service.RefundStatusUpdater;
import uk.gov.pay.connector.service.worldpay.WorldpayNotification;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_ERROR;

class RefundNotificationProcessor {
    private static ImmutableMap<String, RefundStatus> statusMap = ImmutableMap.<String, RefundStatus>builder()
            .put("REFUNDED", REFUNDED)
            .put("REFUNDED_BY_MERCHANT", REFUNDED)
            .put("REFUND_FAILED", REFUND_ERROR)
            .build();

    private Logger logger = LoggerFactory.getLogger(getClass());
    private RefundDao refundDao;
    private RefundStatusUpdater refundStatusUpdater;

    @Inject
    RefundNotificationProcessor(RefundDao refundDao, RefundStatusUpdater refundStatusUpdater) {
        this.refundDao = refundDao;
        this.refundStatusUpdater = refundStatusUpdater;
    }

    public boolean willHandle(WorldpayNotification notification) {
        return statusMap.containsKey(notification.getStatus());
    }

    public RefundStatus newStatus(WorldpayNotification notification) {
        return statusMap.get(notification.getStatus());
    }

    public void invoke(WorldpayNotification notification, ChargeEntity chargeEntity) {
        String reference = notification.getReference();
        if (isBlank(reference)) {
            logger.error("{} refund notification could not be used to update charge (missing reference)",
                    gatewayName());
            return;
        }

        Optional<RefundEntity> optionalRefundEntity = refundDao.findByProviderAndReference(gatewayName().getName(), reference);
        if (!optionalRefundEntity.isPresent()) {
            logger.error("{} notification '{}' could not be used to update refund (associated refund entity not found)",
                    gatewayName(), reference);
            return;
        }

        RefundEntity refundEntity = optionalRefundEntity.get();
        RefundStatus oldStatus = refundEntity.getStatus();

        RefundStatus newStatus = newStatus(notification);
        refundEntity.setStatus(newStatus);
        refundStatusUpdater.updateRefundTransactionStatus(
                gatewayName(), reference, newStatus
        );
        GatewayAccountEntity gatewayAccount = refundEntity.getChargeEntity().getGatewayAccount();
        logger.info("Notification received for refund. Updating refund - charge_external_id={}, refund_reference={}, transaction_id={}, status={}, "
                        + "status_to={}, account_id={}, provider={}, provider_type={}",
                refundEntity.getChargeEntity().getExternalId(),
                reference,
                notification.getTransactionId(),
                oldStatus,
                newStatus,
                gatewayAccount.getId(),
                gatewayAccount.getGatewayName(),
                gatewayAccount.getType());
    }

    public PaymentGatewayName gatewayName() {
        return PaymentGatewayName.WORLDPAY;
    }
}
