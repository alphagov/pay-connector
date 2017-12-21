package uk.gov.pay.connector.service.worldpay;

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
import uk.gov.pay.connector.service.ChargeStatusUpdater;
import uk.gov.pay.connector.service.InterpretedStatus;
import uk.gov.pay.connector.service.MappedChargeStatus;
import uk.gov.pay.connector.service.MappedRefundStatus;
import uk.gov.pay.connector.service.PaymentProviders;
import uk.gov.pay.connector.service.RefundStatusUpdater;
import uk.gov.pay.connector.util.DnsUtils;
import uk.gov.pay.connector.util.XMLUnmarshallerException;

import javax.inject.Inject;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class WorldpayNotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final RefundDao refundDao;
    private final PaymentProviders paymentProviders;
    private final DnsUtils dnsUtils;
    private final ChargeStatusUpdater chargeStatusUpdater;
    private final RefundStatusUpdater refundStatusUpdater;
    private final String gatewayName;
    private final WorldpayPaymentProvider paymentProvider;

    @Inject
    public WorldpayNotificationService(ChargeDao chargeDao, ChargeEventDao chargeEventDao, RefundDao refundDao, PaymentProviders paymentProviders, DnsUtils dnsUtils, ChargeStatusUpdater chargeStatusUpdater, RefundStatusUpdater refundStatusUpdater) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.refundDao = refundDao;
        this.paymentProviders = paymentProviders;
        this.dnsUtils = dnsUtils;
        this.chargeStatusUpdater = chargeStatusUpdater;
        this.refundStatusUpdater = refundStatusUpdater;
        this.paymentProvider = paymentProviders.getWorldpayProvider();
        this.gatewayName = paymentProvider.getPaymentGatewayName().getName();
    }

    @Transactional
    public boolean handleNotificationFor(String ipAddress, String payload) {
        if (paymentProvider().isNotificationEndpointSecured() && !(dnsUtils.ipMatchesDomain(ipAddress, paymentProvider().getNotificationDomain()))) {
            logger.error("{} notification received from domain not {}", paymentProvider().getPaymentGatewayName().getName(), paymentProvider().getNotificationDomain());
            return false;
        }

        WorldpayNotification notification;
        try {
            logger.info("Parsing {} notification", gatewayName());
            notification = paymentProvider().parseNotification(payload);
            logger.info("Parsed {} notification: {}", gatewayName(), notification);
        } catch (XMLUnmarshallerException e) {
            logger.error("{} notification parsing failed: {}", gatewayName(), e.toString());
            return true;
        }

        if (WorldpayStatusMapper.ignored(notification.getStatus())) {
            logger.info("{} notification {} ignored", gatewayName(), notification);
            return true;
        }

        if (isBlank(notification.getTransactionId())) {
            logger.error("{} notification {} failed verification because it has no transaction ID", gatewayName(), notification);
            return true;
        }

        Optional<ChargeEntity> optionalChargeEntity = chargeDao.findByProviderAndTransactionId(gatewayName(),
                notification.getTransactionId());

        if (!optionalChargeEntity.isPresent()) {
            logger.error("{} notification {} could not be evaluated (associated charge entity not found)",
                    gatewayName(), notification);
            return true;
        }

        InterpretedStatus interpretedStatus = WorldpayStatusMapper.from(notification.getStatus());
        if (interpretedStatus instanceof MappedChargeStatus) {
            updateChargeStatus(optionalChargeEntity.get(), interpretedStatus.getChargeStatus(), notification);
        } else if (interpretedStatus instanceof MappedRefundStatus) {
            updateRefundStatus(notification, interpretedStatus.getRefundStatus());
        } else {
            logger.error("{} notification {} unknown", gatewayName(), notification);
        }
        return true;
    }

    private void updateRefundStatus(WorldpayNotification notification, RefundStatus newStatus) {
        if (isBlank(notification.getReference())) {
            logger.error("{} notification {} for refund could not be used to update charge (missing reference)",
                    gatewayName(), notification);
            return;
        }

        Optional<RefundEntity> optionalRefundEntity = refundDao.findByProviderAndReference(gatewayName(), notification.getReference());
        if (!optionalRefundEntity.isPresent()) {
            logger.error("{} notification {} could not be used to update charge (associated charge entity not found)",
                    gatewayName(), notification);
            return;
        }

        RefundEntity refundEntity = optionalRefundEntity.get();
        RefundStatus oldStatus = refundEntity.getStatus();

        refundEntity.setStatus(newStatus);
        refundStatusUpdater.updateRefundTransactionStatus(
                paymentProvider().getPaymentGatewayName(), notification.getReference(), newStatus
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

    private void updateChargeStatus(ChargeEntity chargeEntity, ChargeStatus newStatus, WorldpayNotification notification) {
        String oldStatus = chargeEntity.getStatus();

        try {
            chargeEntity.setStatus(newStatus);
        } catch (InvalidStateTransitionException e) {
            logger.error("{} notification {} could not be used to update charge: {}", gatewayName(), notification, e.getMessage());
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

        chargeEventDao.persistChargeEventOf(chargeEntity, Optional.ofNullable(notification.getGatewayEventDate()));
        chargeStatusUpdater.updateChargeTransactionStatus(chargeEntity.getExternalId(), newStatus, notification.getGatewayEventDate());
    }

    private String gatewayName() {
        return gatewayName;
    }

    private WorldpayPaymentProvider paymentProvider() {
        return paymentProvider;
    }
}
