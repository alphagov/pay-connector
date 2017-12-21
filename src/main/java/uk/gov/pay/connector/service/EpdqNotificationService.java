package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.epdq.EpdqNotification;
import uk.gov.pay.connector.service.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.util.DnsUtils;

import javax.inject.Inject;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class EpdqNotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final RefundDao refundDao;
    private final ChargeStatusUpdater chargeStatusUpdater;
    private final RefundStatusUpdater refundStatusUpdater;
    private final EpdqPaymentProvider paymentProvider;

    @Inject
    public EpdqNotificationService(ChargeDao chargeDao, ChargeEventDao chargeEventDao, RefundDao refundDao, PaymentProviders paymentProviders, ChargeStatusUpdater chargeStatusUpdater, RefundStatusUpdater refundStatusUpdater) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.refundDao = refundDao;
        this.chargeStatusUpdater = chargeStatusUpdater;
        this.refundStatusUpdater = refundStatusUpdater;
        paymentProvider = paymentProviders.getEpdqProvider();
    }

    @Transactional
    public void handleNotificationFor(String payload) {
        logger.info("Parsing {} notification", providerName());

        EpdqNotification notification;
        try {
            notification = paymentProvider.parseNotification(payload);
            logger.info("Parsed {} notification: {}", providerName(), notification.toString());
        }
        catch (EpdqNotification.EpdqParseException e) {
            logger.error("{} notification parsing failed: {}", providerName(), e.toString());
            return;
        }

        if (shouldIgnore(notification)) {
            logger.info("{} notification {} ignored", providerName(), notification);
            return;
        }

        logger.info("Verifying {} notification {}", providerName(), notification);

        if (isBlank(notification.getTransactionId())) {
            logger.error("{} notification {} failed verification because it has no transaction ID", providerName(), notification);
            return;
        }

        Optional<ChargeEntity> maybeCharge = chargeDao.findByProviderAndTransactionId(providerName(), notification.getTransactionId());

        if (!maybeCharge.isPresent()) {
            logger.error("{} notification {} could not be verified (associated charge entity not found)",
                    providerName(), notification);
            return;
        }

        ChargeEntity charge = maybeCharge.get();
        if (!paymentProvider.verifyNotification(notification, charge.getGatewayAccount())) {
            logger.error("{} notification {} failed verification", providerName(), notification);
            return;
        }

        logger.info("Evaluating {} notification {}", providerName(), notification);

        InterpretedStatus interpretedStatus = paymentProvider.from(notification.getStatus(), ChargeStatus.fromString(charge.getStatus()));

        if (interpretedStatus instanceof MappedChargeStatus) {
            updateChargeStatus(notification, interpretedStatus.getChargeStatus());
        } else if (interpretedStatus instanceof MappedRefundStatus) {
            updateRefundStatus(notification, interpretedStatus.getRefundStatus());
        } else {
            logger.error("{} notification {} unknown", providerName(), notification);
        }
    }

    private String providerName() {
        return paymentProvider.getPaymentGatewayName().getName();
    }

    private boolean shouldIgnore(EpdqNotification notification) {
        return paymentProvider.from(notification.getStatus()).getType() == InterpretedStatus.Type.IGNORED;
    }

    private void updateChargeStatus(EpdqNotification notification, ChargeStatus newStatus) {
        Optional<ChargeEntity> optionalChargeEntity = chargeDao.findByProviderAndTransactionId(providerName(), notification.getTransactionId());

        if (!optionalChargeEntity.isPresent()) {
            logger.error("{} notification {} could not be used to update charge (associated charge entity not found)",
                    providerName(), notification);
            return;
        }

        ChargeEntity chargeEntity = optionalChargeEntity.get();
        String oldStatus = chargeEntity.getStatus();

        try {
            chargeEntity.setStatus(newStatus);
        } catch (InvalidStateTransitionException e) {
            logger.error("{} notification {} could not be used to update charge: {}", providerName(), notification, e.getMessage());
            return;
        }

        GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
        logger.info("Notification received. Updating charge - charge_external_id={}, status={}, status_to={}, transaction_id={}, account_id={}, "
                        + "provider={}, provider_type={}",
                chargeEntity.getExternalId(),
                oldStatus,
                newStatus,
                notification.getTransactionId(),
                gatewayAccount.getId(),
                gatewayAccount.getGatewayName(),
                gatewayAccount.getType());

        chargeEventDao.persistChargeEventOf(chargeEntity, Optional.empty());
        chargeStatusUpdater.updateChargeTransactionStatus(chargeEntity.getExternalId(), newStatus, null);
    }

    private void updateRefundStatus(EpdqNotification notification, RefundStatus newStatus) {
        if (isBlank(notification.getReference())) {
            logger.error("{} notification {} for refund could not be used to update charge (missing reference)",
                    providerName(), notification);
            return;
        }

        Optional<RefundEntity> optionalRefundEntity = refundDao.findByProviderAndReference(providerName(), notification.getReference());
        if (!optionalRefundEntity.isPresent()) {
            logger.error("{} notification {} could not be used to update charge (associated charge entity not found)",
                    providerName(), notification);
            return;
        }

        RefundEntity refundEntity = optionalRefundEntity.get();
        RefundStatus oldStatus = refundEntity.getStatus();

        refundEntity.setStatus(newStatus);
        refundStatusUpdater.updateRefundTransactionStatus(
                paymentProvider.getPaymentGatewayName(), notification.getReference(), newStatus
        );
        GatewayAccountEntity gatewayAccount = refundEntity.getChargeEntity().getGatewayAccount();
        logger.info("Notification received for refund. Updating refund - charge_external_id={}, refund_reference={}, transaction_id={}, status={}, "
                        + "status_to={}, account_id={}, provider={}, provider_type={}",
                refundEntity.getChargeEntity().getExternalId(),
                notification.getReference(),
                notification.getTransactionId(),
                oldStatus,
                newStatus,
                gatewayAccount.getId(),
                gatewayAccount.getGatewayName(),
                gatewayAccount.getType());
    }

}
