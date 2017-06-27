package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provider;
import fj.data.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.model.ExtendedNotification;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.Status;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.transaction.NonTransactionalOperation;
import uk.gov.pay.connector.service.transaction.TransactionContext;
import uk.gov.pay.connector.service.transaction.TransactionFlow;
import uk.gov.pay.connector.service.transaction.TransactionalOperation;
import uk.gov.pay.connector.util.DnsUtils;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;

public class NotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeDao chargeDao;
    private final RefundDao refundDao;
    private final PaymentProviders paymentProviders;
    private Provider<TransactionFlow> transactionFlowProvider;
    private final DnsUtils dnsUtils;

    @Inject
    public NotificationService(
            ChargeDao chargeDao,
            RefundDao refundDao,
            PaymentProviders paymentProviders,
            Provider<TransactionFlow> transactionFlowProvider,
            DnsUtils dnsUtils) {
        this.chargeDao = chargeDao;
        this.refundDao = refundDao;
        this.paymentProviders = paymentProviders;
        this.transactionFlowProvider = transactionFlowProvider;
        this.dnsUtils = dnsUtils;
    }


    public boolean handleNotificationFor(String ipAddress, PaymentGatewayName paymentGatewayName, String payload) {
        PaymentProvider paymentProvider = paymentProviders.byName(paymentGatewayName);
        Handler handler = new Handler(paymentProvider);
        if (handler.hasSecuredEndpoint() && !handler.matchesIpWithDomain(ipAddress)) {
            logger.error(format("received notification from domain different than '%s'", paymentProvider.getNotificationDomain()));
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

        public void execute(String payload) {
            transactionFlowProvider.get()
                    .executeNext(prepare(payload))
                    .executeNext(finish())
                    .complete();
        }

        private <T> NonTransactionalOperation<TransactionContext, List<ExtendedNotification<T>>> prepare(String payload) {
            return context -> {

                Either<String, Notifications<T>> notificationsMaybe = paymentProvider.parseNotification(payload);

                if (notificationsMaybe.isLeft()) {
                    logger.error(format("Notification parsing failed: %s", notificationsMaybe.left().value()));
                    return new ArrayList();
                }

                ImmutableList<Notification<T>> notifications = notificationsMaybe.right().value().get();

                logger.info("Handling notification from provider={}, notification={}", paymentProvider.getPaymentGatewayName(), notifications);

                List<ExtendedNotification<T>> extendedNotifications = notifications.stream()
                        .map(toExtendedNotification())
                        .filter(isValid())
                        .collect(Collectors.toList());

                return extendedNotifications;
            };
        }

        private <T> TransactionalOperation<TransactionContext, Void> finish() {
            return context -> {
                List<ExtendedNotification<T>> notifications = context.get(ArrayList.class);
                notifications.forEach(
                        notification -> {
                            if (notification.getInterpretedStatus().isIgnored()) {
                                logger.info(format("Notification ignored - paymentProvider=%s, status=%s, transaction_id=%s, reference=%s.",
                                        paymentProvider.getPaymentGatewayName(), notification.getStatus(), notification.getTransactionId(), notification.getReference()));
                                return;
                            } else if (notification.getInterpretedStatus().isUnknown()) {
                                logger.error(format("Notification unknown - paymentProvider=%s, status=%s, transaction_id=%s, reference=%s.",
                                        paymentProvider.getPaymentGatewayName(), notification.getStatus(), notification.getTransactionId(), notification.getReference()));
                                return;
                            }

                            updateChargeStatusFromNotification(notification);
                        });
                return null;
            };
        }

        private void updateChargeStatusFromNotification(ExtendedNotification notification) {
            Optional<Status> newStatus =
                notification.getInterpretedStatus().isDeferred() ?
                    resolveDeferredInterpretedStatus(notification, (BaseStatusMapper.DeferredStatus) notification.getInterpretedStatus()) :
                    notification.getInterpretedStatus().get();

            newStatus.ifPresent(status -> {
                if (notification.isOfChargeType()) {
                    updateChargeStatus(notification, status);
                    return;
                }

                if (notification.isOfRefundType()) {
                    if (isBlank(notification.getReference())) {
                        logger.error(format("Notification with transaction_id=%s of type refund with no reference ignored.",
                            notification.getTransactionId()));
                        return;
                    }
                    updateRefundStatus(notification, status);
                    return;
                }

                logger.error(format("Notification with transaction_id=%s and status=%s is neither of type charge nor refund",
                    notification.getTransactionId(), newStatus));
                return;
            });
        }

        private <T> Optional<Status> resolveDeferredInterpretedStatus(ExtendedNotification<T> notification, BaseStatusMapper.DeferredStatus deferredStatus) {
            Optional<ChargeEntity> optionalChargeEntity = chargeDao.findByProviderAndTransactionId(
                    paymentProvider.getPaymentGatewayName(), notification.getTransactionId());
            if (!optionalChargeEntity.isPresent()) {
                logger.error(format("Notification could not be resolved (unable to find charge entity) - paymentProvider=%s, transaction_id=%s, status:%s.",
                        paymentProvider.getPaymentGatewayName(), notification.getTransactionId(), notification.getInterpretedStatus().get().get()));
                return Optional.empty();
            }

            return deferredStatus.getDeferredStatusResolver().resolve(optionalChargeEntity.get());
        }

        private <T> void updateChargeStatus(ExtendedNotification<T> notification, Status newStatus) {
            Optional<ChargeEntity> optionalChargeEntity = chargeDao.findByProviderAndTransactionId(
                    paymentProvider.getPaymentGatewayName(), notification.getTransactionId());
            if (!optionalChargeEntity.isPresent()) {
                logger.error(format("Notification with transaction_id=%s failed updating charge status to: %s. Unable to find charge entity.",
                        notification.getTransactionId(), newStatus));
                return;
            }
            ChargeEntity chargeEntity = optionalChargeEntity.get();
            String oldStatus = chargeEntity.getStatus();

            try {
                chargeEntity.setStatus((ChargeStatus) newStatus);
            } catch (InvalidStateTransitionException e) {
                logger.error(format("Notification with transaction id=%s failed updating charge status to: %s. Error: %s",
                        notification.getTransactionId(), newStatus, e.getMessage()));
                return;
            }

            GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
            logger.info("Notification received. Updating charge - charge_external_id={}, status={}, status_to={}, transaction_id={}, account_id={}, provider={}, provider_type={}",
                    chargeEntity.getExternalId(),
                    oldStatus,
                    newStatus,
                    notification.getTransactionId(),
                    gatewayAccount.getId(),
                    gatewayAccount.getGatewayName(),
                    gatewayAccount.getType());

            chargeDao.mergeAndNotifyStatusHasChanged(chargeEntity, Optional.ofNullable(notification.getGatewayEventDate()));
        }

        private <T> void updateRefundStatus(ExtendedNotification<T> notification, Status newStatus) {
            Optional<RefundEntity> optionalRefundEntity = refundDao.findByReference(notification.getReference());
            if (!optionalRefundEntity.isPresent()) {
                logger.error(format("Notification with transaction_id=%s and reference=%s failed updating refund status to: %s. Unable to find refund entity.",
                        notification.getTransactionId(), notification.getReference(), newStatus));
                return;
            }
            RefundEntity refundEntity = optionalRefundEntity.get();
            RefundStatus oldStatus = refundEntity.getStatus();

            refundEntity.setStatus((RefundStatus) newStatus);

            GatewayAccountEntity gatewayAccount = refundEntity.getChargeEntity().getGatewayAccount();
            logger.info("Notification received for refund. Updating refund - charge_external_id={}, refund_reference={}, transaction_id={}, status={}, status_to={}, account_id={}, provider={}, provider_type={}",
                    refundEntity.getChargeEntity().getExternalId(),
                    notification.getReference(),
                    notification.getTransactionId(),
                    oldStatus,
                    newStatus,
                    gatewayAccount.getId(),
                    gatewayAccount.getGatewayName(),
                    gatewayAccount.getType());
        }

        private <T> Function<Notification<T>, ExtendedNotification<T>> toExtendedNotification() {
            return notification ->ExtendedNotification.extend(notification, paymentProvider.getStatusMapper().from(notification.getStatus()));
        }

        private <T> Predicate<ExtendedNotification<T>> isValid() {
            return notification -> {
                if (!isNotBlank(notification.getTransactionId())) {
                    logger.error("{} notification with no transaction ID ignored.", paymentProvider.getPaymentGatewayName());
                    return false;
                }

                if (!paymentProvider.verifyNotification(notification, getPassphrase(notification))) {
                    logger.error("{} notification with transaction ID {} ignored because it could not be verified.", paymentProvider.getPaymentGatewayName(), notification.getTransactionId());
                    return false;
                }

                return true;
            };
        }

        private String getPassphrase(ExtendedNotification notification) {
            Optional<ChargeEntity> optionalChargeEntity = chargeDao.findByProviderAndTransactionId(
                paymentProvider.getPaymentGatewayName(), notification.getTransactionId());
            if (!optionalChargeEntity.isPresent()) {
                logger.error(format("Notification with transaction_id=%s. Unable to find charge entity.", notification.getTransactionId()));
                return null;
            }

            return optionalChargeEntity.get().getGatewayAccount().getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE);
        }

    }
}
