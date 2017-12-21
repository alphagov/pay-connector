package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeEventDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.model.EvaluatedChargeStatusNotification;
import uk.gov.pay.connector.model.EvaluatedNotification;
import uk.gov.pay.connector.model.EvaluatedRefundStatusNotification;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.smartpay.SmartpayNotification;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.util.DnsUtils;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class SmartpayNotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeDao chargeDao;
    private final ChargeEventDao chargeEventDao;
    private final RefundDao refundDao;
    private final ChargeStatusUpdater chargeStatusUpdater;
    private final RefundStatusUpdater refundStatusUpdater;
    private final SmartpayPaymentProvider paymentProvider;

    @Inject
    public SmartpayNotificationService(ChargeDao chargeDao, ChargeEventDao chargeEventDao, RefundDao refundDao, PaymentProviders paymentProviders, DnsUtils dnsUtils, ChargeStatusUpdater chargeStatusUpdater, RefundStatusUpdater refundStatusUpdater) {
        this.chargeDao = chargeDao;
        this.chargeEventDao = chargeEventDao;
        this.refundDao = refundDao;
        this.chargeStatusUpdater = chargeStatusUpdater;
        this.refundStatusUpdater = refundStatusUpdater;
        paymentProvider = paymentProviders.getSmartPayProvider();
    }

    @Transactional
    public boolean handleNotificationFor(String ipAddress, PaymentGatewayName paymentGatewayName, String payload) {
        List<SmartpayNotification> notifications = parse(payload);
        for (SmartpayNotification notification : notifications) {
            handle(notification);
        }
        return true;
    }

    private void handle(SmartpayNotification notification) {
        if (shouldIgnore(notification)) {
            logger.info("{} notification {} ignored", providerName(), notification);
            return;
        }

        logger.info("Verifying {} notification {}", providerName(), notification);

        if (isBlank(notification.getTransactionId())) {
            logger.error("{} notification {} failed verification because it has no transaction ID", providerName(), notification);
            return;
        }

        logger.info("Evaluating {} notification {}", providerName(), notification);

        Optional<ChargeEntity> maybeCharge = chargeDao.findByProviderAndTransactionId(providerName(),
                notification.getOriginalReference());

        if (!maybeCharge.isPresent()) {
            logger.error("{} notification {} could not be evaluated (associated charge entity not found)",
                    providerName(), notification);
            return;
        }

        ChargeEntity charge = maybeCharge.get();
        InterpretedStatus interpretedStatus = interpretStatus(notification, charge);

        if (interpretedStatus instanceof MappedChargeStatus) {
            updateChargeStatus(charge, notification, interpretedStatus.getChargeStatus());
        } else if (interpretedStatus instanceof MappedRefundStatus) {
            updateRefundStatus(notification, interpretedStatus.getRefundStatus());
        } else {
            logger.error("{} notification {} unknown", providerName(), notification);
        }
    }

    private InterpretedStatus interpretStatus(SmartpayNotification notification, ChargeEntity charge) {
        return paymentProvider.from(notification.getStatus(), ChargeStatus.fromString(charge.getStatus()));
    }

    private boolean shouldIgnore(SmartpayNotification notification) {
        return paymentProvider.from(notification.getStatus()).getType() == InterpretedStatus.Type.IGNORED;
    }

    private List<SmartpayNotification> parse(String payload) {
        logger.info("Parsing {} notification", providerName());

        List<SmartpayNotification> result;
        try {
            result = paymentProvider.parseNotification(payload);
            logger.info("Parsed {} notification: {}", providerName(), result);
        }
        catch(SmartpayPaymentProvider.SmartpayParseError e) {
            logger.error("{} notification parsing failed: {}", providerName(), e.toString());
            result = Collections.emptyList();
        }
        return result;
    }

    private String providerName() {
        return paymentProvider.getPaymentGatewayName().getName();
    }


    private void updateChargeStatus(ChargeEntity chargeEntity, SmartpayNotification notification, ChargeStatus newStatus) {
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
                notification.getOriginalReference(),
                gatewayAccount.getId(),
                gatewayAccount.getGatewayName(),
                gatewayAccount.getType());

        chargeEventDao.persistChargeEventOf(chargeEntity, Optional.ofNullable(notification.getEventDate()));
        chargeStatusUpdater.updateChargeTransactionStatus(chargeEntity.getExternalId(), newStatus, notification.getEventDate());
    }
/*
String transactionId, notification.getOriginalReference(),
String reference, notification.getPspReference(),
T status,  notification.getStatus(),
ZonedDateTime generationTime,  notification.getEventDate(),
List<NameValuePair> payload, null

                    ));

 */
    private void updateRefundStatus(SmartpayNotification notification, RefundStatus newStatus) {
        if (isBlank(notification.getPspReference())) {
            logger.error("{} notification {} for refund could not be used to update charge (missing reference)",
                    providerName(), notification);
            return;
        }

        Optional<RefundEntity> optionalRefundEntity = refundDao.findByProviderAndReference(providerName(), notification.getPspReference());
        if (!optionalRefundEntity.isPresent()) {
            logger.error("{} notification {} could not be used to update charge (associated charge entity not found)",
                    providerName(), notification);
            return;
        }

        RefundEntity refundEntity = optionalRefundEntity.get();
        RefundStatus oldStatus = refundEntity.getStatus();

        refundEntity.setStatus(newStatus);
        refundStatusUpdater.updateRefundTransactionStatus(
                paymentProvider.getPaymentGatewayName(), notification.getPspReference(), newStatus
        );
        GatewayAccountEntity gatewayAccount = refundEntity.getChargeEntity().getGatewayAccount();
        logger.info("Notification received for refund. Updating refund - charge_external_id={}, refund_reference={}, transaction_id={}, status={}, "
                        + "status_to={}, account_id={}, provider={}, provider_type={}",
                refundEntity.getChargeEntity().getExternalId(),
                notification.getPspReference(),
                notification.getOriginalReference(),
                oldStatus,
                newStatus,
                gatewayAccount.getId(),
                gatewayAccount.getGatewayName(),
                gatewayAccount.getType());
    }
}
