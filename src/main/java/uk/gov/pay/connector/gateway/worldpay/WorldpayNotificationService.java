package uk.gov.pay.connector.gateway.worldpay;

import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gateway.util.XMLUnmarshallerException;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.util.DnsUtils;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;

public class WorldpayNotificationService {

    private static final List<String> IGNORED_STATUSES = ImmutableList.of(
            "SENT_FOR_AUTHORISATION",
            "AUTHORISED",
            "CANCELLED",
            "EXPIRED",
            "REFUSED",
            "REFUSED_BY_BANK",
            "SETTLED_BY_MERCHANT",
            "SENT_FOR_REFUND"
    );
    private static final List<String> REFUND_STATUSES = ImmutableList.of("REFUNDED", "REFUNDED_BY_MERCHANT", "REFUND_FAILED");
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeDao chargeDao;
    private final WorldpayNotificationConfiguration config;
    private final DnsUtils dnsUtils;
    private final ChargeNotificationProcessor chargeNotificationProcessor;
    private final RefundNotificationProcessor refundNotificationProcessor;

    @Inject
    public WorldpayNotificationService(
            ChargeDao chargeDao,
            WorldpayNotificationConfiguration config,
            DnsUtils dnsUtils,
            ChargeNotificationProcessor chargeNotificationProcessor,
            RefundNotificationProcessor refundNotificationProcessor
    ) {
        this.chargeDao = chargeDao;
        this.config = config;
        this.dnsUtils = dnsUtils;

        this.chargeNotificationProcessor = chargeNotificationProcessor;
        this.refundNotificationProcessor = refundNotificationProcessor;
    }

    @Transactional
    public boolean handleNotificationFor(String ipAddress, String payload) {
        if (isNotificationRejectedFromIpAddress(ipAddress)) {
            logger.error("{} notification received from ip '{}' which is not in domain '{}'", gatewayName(), ipAddress, notificationDomain());
            return false;
        }

        WorldpayNotification notification;
        try {
            logger.info("Parsing {} notification", gatewayName());
            logger.debug("Payload: {}", payload);
            notification = XMLUnmarshaller.unmarshall(payload, WorldpayNotification.class);
            logger.info("Parsed {} notification: {}", gatewayName(), notification);
        } catch (XMLUnmarshallerException e) {
            logger.error("{} notification parsing failed: {}", gatewayName(), e.toString());
            return true;
        }

        if (isIgnored(notification)) {
            logger.info("{} notification {} ignored", gatewayName(), notification);
            return true;
        }

        if (isTransactionIdBlank(notification)) {
            logger.error("{} notification {} failed verification because it has no transaction ID", gatewayName(), notification);
            return true;
        }

        Optional<ChargeEntity> optionalChargeEntity = chargeDao.findByProviderAndTransactionId(gatewayName(),
                notification.getTransactionId());

        if (optionalChargeEntity.isEmpty()) {
            logger.error("{} notification {} could not be evaluated (associated charge entity not found)",
                    gatewayName(), notification);
            return true;
        }

        if (isCaptureNotification(notification)) {
            chargeNotificationProcessor.invoke(notification.getTransactionId(), optionalChargeEntity.get(), CAPTURED, notification.getGatewayEventDate());
        } else if (isRefundNotification(notification)) {
            refundNotificationProcessor.invoke(getPaymentGatewayName(), newRefundStatus(notification), notification.getReference(), notification.getTransactionId());
        } else {
            logger.error("{} notification {} unknown", gatewayName(), notification);
        }
        return true;
    }

    private RefundStatus newRefundStatus(WorldpayNotification notification) {
        return "REFUND_FAILED".equals(notification.getStatus()) ? RefundStatus.REFUND_ERROR : RefundStatus.REFUNDED;
    }

    private boolean isRefundNotification(WorldpayNotification notification) {
        return REFUND_STATUSES.contains(notification.getStatus());
    }

    private boolean isCaptureNotification(WorldpayNotification notification) {
        return "CAPTURED".equals(notification.getStatus());
    }

    private boolean isNotificationRejectedFromIpAddress(String ipAddress) {
        return isNotificationEndpointSecured() && !dnsUtils.ipMatchesDomain(ipAddress, notificationDomain());
    }

    private boolean isTransactionIdBlank(WorldpayNotification notification) {
        return isBlank(notification.getTransactionId());
    }

    private boolean isIgnored(WorldpayNotification notification) {
        return IGNORED_STATUSES.contains(notification.getStatus());
    }

    public String notificationDomain() {
        return config.getNotificationDomain();
    }

    public Boolean isNotificationEndpointSecured() {
        return config.isNotificationEndpointSecured();
    }

    public PaymentGatewayName getPaymentGatewayName() {
        return PaymentGatewayName.WORLDPAY;
    }

    public String gatewayName() {
        return getPaymentGatewayName().getName();
    }
}
