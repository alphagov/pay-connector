package uk.gov.pay.connector.provider.worldpay;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.PaymentGatewayName;
import uk.gov.pay.connector.service.worldpay.WorldpayNotification;
import uk.gov.pay.connector.util.DnsUtils;
import uk.gov.pay.connector.util.XMLUnmarshaller;
import uk.gov.pay.connector.util.XMLUnmarshallerException;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class WorldpayNotificationService {

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

        if (!optionalChargeEntity.isPresent()) {
            logger.error("{} notification {} could not be evaluated (associated charge entity not found)",
                    gatewayName(), notification);
            return true;
        }

        if (isCaptureNotification(notification)) {
            chargeNotificationProcessor.invoke(optionalChargeEntity.get(), notification.getTransactionId(), notification.getGatewayEventDate());
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
        return Arrays.asList("REFUNDED", "REFUNDED_BY_MERCHANT", "REFUND_FAILED").contains(notification.getStatus());
    }

    private boolean isCaptureNotification(WorldpayNotification notification) {
        return notification.getStatus().equals("CAPTURED");
    }

    private boolean isNotificationRejectedFromIpAddress(String ipAddress) {
        return isNotificationEndpointSecured() && !dnsUtils.ipMatchesDomain(ipAddress, notificationDomain());
    }

    private boolean isTransactionIdBlank(WorldpayNotification notification) {
        return isBlank(notification.getTransactionId());
    }

    private boolean isIgnored(WorldpayNotification notification) {
        return Arrays.asList(
                "SENT_FOR_AUTHORISATION",
                "AUTHORISED",
                "CANCELLED",
                "EXPIRED",
                "REFUSED",
                "REFUSED_BY_BANK",
                "SETTLED_BY_MERCHANT",
                "SENT_FOR_REFUND"
        ).contains(notification.getStatus());
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
