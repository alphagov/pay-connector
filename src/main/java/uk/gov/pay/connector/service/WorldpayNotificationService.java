package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.model.EvaluatedChargeStatusNotification;
import uk.gov.pay.connector.model.EvaluatedNotification;
import uk.gov.pay.connector.model.EvaluatedRefundStatusNotification;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayStatusMapper;
import uk.gov.pay.connector.util.DnsUtils;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
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

    @Inject
    public WorldpayNotificationService(ChargeDao chargeDao, ChargeEventDao chargeEventDao, RefundDao refundDao, PaymentProviders paymentProviders, DnsUtils dnsUtils, ChargeStatusUpdater chargeStatusUpdater, RefundStatusUpdater refundStatusUpdater) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.refundDao = refundDao;
        this.paymentProviders = paymentProviders;
        this.dnsUtils = dnsUtils;
        this.chargeStatusUpdater = chargeStatusUpdater;
        this.refundStatusUpdater = refundStatusUpdater;
    }

    public boolean handleNotificationFor(String ipAddress, PaymentGatewayName paymentGatewayName, String payload) {
        if (paymentGatewayName != PaymentGatewayName.WORLDPAY) {
            throw new RuntimeException("WorldpayNotificationService may only be used with Worldpay payment gateway (got " + paymentGatewayName.toString() + ")");
        }
        return handleNotificationFor(ipAddress, payload);
    }

    @Transactional
    public boolean handleNotificationFor(String ipAddress, String payload) {
        WorldpayPaymentProvider paymentProvider = paymentProviders.getWorldpayProvider();
        Handler handler = new Handler(paymentProvider);
        if (paymentProvider.isNotificationEndpointSecured() && !(dnsUtils.ipMatchesDomain(ipAddress, paymentProvider.getNotificationDomain()))) {
            logger.error("{} notification received from domain not {}", paymentProvider.getPaymentGatewayName().getName(), paymentProvider.getNotificationDomain());
            return false;
        }
        handler.execute(payload);
        return true;
    }

    private class Handler {
        private WorldpayPaymentProvider paymentProvider;
        private final Logger logger = LoggerFactory.getLogger(getClass());

        public Handler(WorldpayPaymentProvider paymentProvider) {
            this.paymentProvider = paymentProvider;
        }

        public void execute(String payload) {
            List<Notification<String>> notifications = parse(payload);
            notifications.forEach(notification -> Optional.of(notification)
                    .filter(this::ignoreEarly)
                    .filter(this::verify)
                    .flatMap(this::evaluate)
                    .ifPresent(this::update));
        }

        private List<Notification<String>> parse(String payload) {
            logger.info("Parsing {} notification", gatewayName());

            Either<String, Notifications<String>> notificationsMaybe = paymentProvider.parseNotification(payload);

            if (notificationsMaybe.isLeft()) {
                logger.error("{} notification parsing failed: {}", gatewayName(), notificationsMaybe.left().value());
                return Collections.emptyList();
            }

            List<Notification<String>> notifications = notificationsMaybe.right().value().get();
            logger.info("Parsed {} notification: {}", gatewayName(), notifications);
            return notifications;
        }

        /**
         * If the notification is intended to be ignored, we abort immediately to avoid further complications.
         * Depending on the payment provider and the type of the notification we may not always for instance have a
         * transaction id, hence if we intend to ignore the notification it is safer to do it here.
         */
        private boolean ignoreEarly(Notification<String> notification) {

            if (WorldpayStatusMapper.get().from(notification.getStatus()).getType() == InterpretedStatus.Type.IGNORED) {
                logger.info("{} notification {} ignored", gatewayName(), notification);
                return false;
            }

            return true;
        }

        private boolean verify(Notification<String> notification) {
            logger.info("Verifying {} notification {}", gatewayName(), notification);

            if (isBlank(notification.getTransactionId())) {
                logger.error("{} notification {} failed verification because it has no transaction ID", gatewayName(), notification);
                return false;
            }

            return chargeDao.findByProviderAndTransactionId(gatewayName(), notification.getTransactionId())
                    .map(charge -> {
                        if (paymentProvider.verifyNotification(notification, charge.getGatewayAccount())) {
                            return true;
                        }
                        logger.error("{} notification {} failed verification", gatewayName(), notification);
                        return false;
                    })
                    .orElseGet(() -> {
                        logger.error("{} notification {} could not be verified (associated charge entity not found)",
                                gatewayName(), notification);
                        return false;
                    });
        }

        private String gatewayName() {
            return paymentProvider.getPaymentGatewayName().getName();
        }

        private Optional<EvaluatedNotification<String>> evaluate(Notification<String> notification) {
            logger.info("Evaluating {} notification {}", gatewayName(), notification);

            Optional<ChargeEntity> optionalChargeEntity = chargeDao.findByProviderAndTransactionId(gatewayName(),
                    notification.getTransactionId());

            if (!optionalChargeEntity.isPresent()) {
                logger.error("{} notification {} could not be evaluated (associated charge entity not found)",
                        gatewayName(), notification);
                return Optional.empty();
            }

            return optionalChargeEntity.map(charge -> {
                InterpretedStatus status = paymentProvider.getStatusMapper().from(notification.getStatus(), ChargeStatus.fromString(charge.getStatus()));
                switch (status.getType()) {
                    case CHARGE_STATUS:
                        return new EvaluatedChargeStatusNotification<>(notification, status.getChargeStatus());
                    case REFUND_STATUS:
                        return new EvaluatedRefundStatusNotification<>(notification, status.getRefundStatus());
                    case IGNORED:
                        logger.info("{} notification {} ignored", gatewayName(), notification);
                        return null;
                    case UNKNOWN:
                    default:
                        logger.error("{} notification {} unknown", gatewayName(), notification);
                        return null;
                }
            });
        }

        private void update(EvaluatedNotification<String> notification) {
            logger.info("Updating charge per {} notification {}", gatewayName(), notification);

            if (notification.isOfChargeType()) {
                updateChargeStatus((EvaluatedChargeStatusNotification) notification);
                return;
            }

            if (notification.isOfRefundType()) {
                updateRefundStatus((EvaluatedRefundStatusNotification) notification);
                return;
            }

            logger.error("{} notification {} could not be processed because it is of neither charge nor refund type",
                    gatewayName(), notification);
        }

        private void updateChargeStatus(EvaluatedChargeStatusNotification<String> notification) {
            Optional<ChargeEntity> optionalChargeEntity = chargeDao.findByProviderAndTransactionId(gatewayName(), notification.getTransactionId());

            if (!optionalChargeEntity.isPresent()) {
                logger.error("{} notification {} could not be used to update charge (associated charge entity not found)",
                        gatewayName(), notification);
                return;
            }

            ChargeEntity chargeEntity = optionalChargeEntity.get();
            String oldStatus = chargeEntity.getStatus();
            ChargeStatus newStatus = notification.getChargeStatus();

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

        private void updateRefundStatus(EvaluatedRefundStatusNotification<String> notification) {
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
            RefundStatus newStatus = notification.getRefundStatus();

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

}
