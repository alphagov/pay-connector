package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevents.dao.ChargeEventDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.EvaluatedChargeStatusNotification;
import uk.gov.pay.connector.model.EvaluatedNotification;
import uk.gov.pay.connector.model.EvaluatedRefundStatusNotification;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.util.DnsUtils;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class NotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final RefundDao refundDao;
    private final PaymentProviders paymentProviders;
    private final DnsUtils dnsUtils;
    private final UserNotificationService userNotificationService;

    @Inject
    public NotificationService(ChargeDao chargeDao, ChargeEventDao chargeEventDao, RefundDao refundDao, PaymentProviders paymentProviders, DnsUtils dnsUtils, UserNotificationService userNotificationService) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.refundDao = refundDao;
        this.paymentProviders = paymentProviders;
        this.dnsUtils = dnsUtils;
        this.userNotificationService = userNotificationService;
    }

    @Transactional
    public boolean handleNotificationFor(String ipAddress, PaymentGatewayName paymentGatewayName, String payload) {
        PaymentProvider paymentProvider = paymentProviders.byName(paymentGatewayName);
        Handler handler = new Handler(paymentProvider);
        if (handler.hasSecuredEndpoint() && !handler.matchesIpWithDomain(ipAddress)) {
            logger.error("{} notification received from domain not {}", paymentProvider.getPaymentGatewayName().getName(), paymentProvider.getNotificationDomain());
            return false;
        }
        handler.execute(payload);
        return true;
    }

    private class Handler {
        private PaymentProvider paymentProvider;

        public Handler(PaymentProvider paymentProvider) {
            this.paymentProvider = paymentProvider;
        }

        public boolean hasSecuredEndpoint() {
            return paymentProvider.isNotificationEndpointSecured();
        }

        public boolean matchesIpWithDomain(String ipAddress) {
            return (dnsUtils.ipMatchesDomain(ipAddress, paymentProvider.getNotificationDomain()));
        }

        public <T> void execute(String payload) {
            List<Notification<T>> notifications = parse(payload);
            notifications.forEach(notification -> Optional.of(notification)
                    .filter(this::ignoreEarly)
                    .filter(this::verify)
                    .flatMap(this::evaluate)
                    .ifPresent(this::update));
        }

        private <T> List<Notification<T>> parse(String payload) {
            logger.info("Parsing {} notification", paymentProvider.getPaymentGatewayName().getName());

            Either<String, Notifications<T>> notificationsMaybe = paymentProvider.parseNotification(payload);

            if (notificationsMaybe.isLeft()) {
                logger.error("{} notification parsing failed: {}", paymentProvider.getPaymentGatewayName().getName(), notificationsMaybe.left().value());
                return Collections.emptyList();
            }

            List<Notification<T>> notifications = notificationsMaybe.right().value().get();
            logger.info("Parsed {} notification: {}", paymentProvider.getPaymentGatewayName().getName(), notifications);
            return notifications;
        }

        /**
         * If the notification is intended to be ignored, we abort immediately to avoid further complications.
         * Depending on the payment provider and the type of the notification we may not always for instance have a
         * transaction id, hence if we intend to ignore the notification it is safer to do it here.
         */
        private <T> boolean ignoreEarly(Notification<T> notification) {

            if (paymentProvider.getStatusMapper().from(notification.getStatus()).getType() == InterpretedStatus.Type.IGNORED) {
                logger.info("{} notification {} ignored", paymentProvider.getPaymentGatewayName().getName(), notification);
                return false;
            }

            return true;
        }

        private <T> boolean verify(Notification<T> notification) {
            logger.info("Verifying {} notification {}", paymentProvider.getPaymentGatewayName().getName(), notification);

            if (isBlank(notification.getTransactionId())) {
                logger.error("{} notification {} failed verification because it has no transaction ID", paymentProvider.getPaymentGatewayName().getName(), notification);
                return false;
            }

            return chargeDao.findByProviderAndTransactionId(paymentProvider.getPaymentGatewayName().getName(), notification.getTransactionId())
                    .map(charge -> {
                        if (paymentProvider.verifyNotification(notification, charge.getGatewayAccount())) {
                            return true;
                        }
                        logger.error("{} notification {} failed verification", paymentProvider.getPaymentGatewayName().getName(), notification);
                        return false;
                    })
                    .orElseGet(() -> {
                        logger.error("{} notification {} could not be verified (associated charge entity not found)",
                                paymentProvider.getPaymentGatewayName().getName(), notification);
                        return false;
                    });
        }

        private <T> Optional<EvaluatedNotification<T>> evaluate(Notification<T> notification) {
            logger.info("Evaluating {} notification {}", paymentProvider.getPaymentGatewayName().getName(), notification);

            Optional<ChargeEntity> optionalChargeEntity = chargeDao.findByProviderAndTransactionId(paymentProvider.getPaymentGatewayName().getName(),
                    notification.getTransactionId());

            if (!optionalChargeEntity.isPresent()) {
                logger.error("{} notification {} could not be evaluated (associated charge entity not found)",
                        paymentProvider.getPaymentGatewayName().getName(), notification);
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
                        logger.info("{} notification {} ignored", paymentProvider.getPaymentGatewayName().getName(), notification);
                        return null;
                    case UNKNOWN:
                    default:
                        logger.error("{} notification {} unknown", paymentProvider.getPaymentGatewayName().getName(), notification);
                        return null;
                }
            });
        }

        private <T> void update(EvaluatedNotification<T> notification) {
            logger.info("Updating charge per {} notification {}", paymentProvider.getPaymentGatewayName().getName(), notification);

            if (notification.isOfChargeType()) {
                updateChargeStatus((EvaluatedChargeStatusNotification) notification);
                return;
            }

            if (notification.isOfRefundType()) {
                updateRefundStatus((EvaluatedRefundStatusNotification) notification);
                return;
            }

            logger.error("{} notification {} could not be processed because it is of neither charge nor refund type",
                    paymentProvider.getPaymentGatewayName().getName(), notification);
        }

        private <T> void updateChargeStatus(EvaluatedChargeStatusNotification<T> notification) {
            Optional<ChargeEntity> optionalChargeEntity = chargeDao.findByProviderAndTransactionId(paymentProvider.getPaymentGatewayName().getName(), notification.getTransactionId());

            if (!optionalChargeEntity.isPresent()) {
                logger.error("{} notification {} could not be used to update charge (associated charge entity not found)",
                        paymentProvider.getPaymentGatewayName().getName(), notification);
                return;
            }

            ChargeEntity chargeEntity = optionalChargeEntity.get();
            String oldStatus = chargeEntity.getStatus();
            ChargeStatus newStatus = notification.getChargeStatus();

            try {
                chargeEntity.setStatus(newStatus);
            } catch (InvalidStateTransitionException e) {
                logger.error("{} notification {} could not be used to update charge: {}", paymentProvider.getPaymentGatewayName().getName(), notification, e.getMessage());
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
        }

        private <T> void updateRefundStatus(EvaluatedRefundStatusNotification<T> notification) {
            if (isBlank(notification.getReference())) {
                logger.error("{} notification {} for refund could not be used to update charge (missing reference)",
                        paymentProvider.getPaymentGatewayName().getName(), notification);
                return;
            }

            Optional<RefundEntity> optionalRefundEntity = refundDao.findByProviderAndReference(paymentProvider.getPaymentGatewayName().getName(), notification.getReference());
            if (!optionalRefundEntity.isPresent()) {
                logger.error("{} notification {} could not be used to update charge (associated refund entity not found)",
                        paymentProvider.getPaymentGatewayName().getName(), notification);
                return;
            }

            RefundEntity refundEntity = optionalRefundEntity.get();
            RefundStatus oldStatus = refundEntity.getStatus();
            RefundStatus newStatus = notification.getRefundStatus();

            refundEntity.setStatus(newStatus);
            
            if (newStatus.equals(RefundStatus.REFUNDED)) {
                userNotificationService.sendRefundIssuedEmail(refundEntity);
            }
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
