package uk.gov.pay.connector.gateway.worldpay;

import com.google.common.collect.ImmutableList;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gateway.util.XMLUnmarshallerException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.util.IpDomainMatcher;

import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class WorldpayNotificationService {

    private static final String PAYMENT_GATEWAY_NAME = PaymentGatewayName.WORLDPAY.getName();

    private static final List<String> IGNORED_STATUSES = ImmutableList.of(
            "SENT_FOR_AUTHORISATION",
            "AUTHORISED",
            "CANCELLED",
            "EXPIRED",
            "REFUSED",
            "REFUSED_BY_BANK",
            "SETTLED",
            "SETTLED_BY_MERCHANT"
    );
    public static final String NULL_AUTHORISATION_CODE = "null";
    private static final List<String> REFUND_STATUSES = ImmutableList.of("REFUNDED", "REFUNDED_BY_MERCHANT", "REFUND_FAILED", "SENT_FOR_REFUND");
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeService chargeService;
    private final WorldpayNotificationConfiguration config;
    private final IpDomainMatcher ipDomainMatcher;
    private final ChargeNotificationProcessor chargeNotificationProcessor;
    private final RefundNotificationProcessor refundNotificationProcessor;
    private GatewayAccountService gatewayAccountService;

    @Inject
    public WorldpayNotificationService(
            ChargeService chargeService,
            WorldpayNotificationConfiguration config,
            IpDomainMatcher ipDomainMatcher,
            ChargeNotificationProcessor chargeNotificationProcessor,
            RefundNotificationProcessor refundNotificationProcessor,
            GatewayAccountService gatewayAccountService) {
        this.chargeService = chargeService;
        this.config = config;
        this.ipDomainMatcher = ipDomainMatcher;

        this.chargeNotificationProcessor = chargeNotificationProcessor;
        this.refundNotificationProcessor = refundNotificationProcessor;
        this.gatewayAccountService = gatewayAccountService;
    }

    @Transactional
    public boolean handleNotificationFor(String ipAddress, String payload) {
        if (isNotificationRejectedFromIpAddress(ipAddress)) {
            logger.error("{} notification received from ip '{}' which is not in domain '{}'", PAYMENT_GATEWAY_NAME,
                    ipAddress, notificationDomain());
            return false;
        }

        WorldpayNotification notification;
        try {
            logger.info("Parsing {} notification", PAYMENT_GATEWAY_NAME);
            logger.debug("Payload: {}", payload);
            notification = XMLUnmarshaller.unmarshall(payload, WorldpayNotification.class);
            logger.info("Parsed {} notification: {}", PAYMENT_GATEWAY_NAME, notification);
        } catch (XMLUnmarshallerException e) {
            logger.error("{} notification parsing failed: {}", PAYMENT_GATEWAY_NAME, e);
            return true;
        }

        if (isIgnored(notification)) {
            logger.info("{} notification {} ignored", PAYMENT_GATEWAY_NAME, notification);
            return true;
        }

        if (isTransactionIdBlank(notification)) {
            logger.warn("{} notification {} failed verification because it has no transaction ID", 
                    PAYMENT_GATEWAY_NAME, notification);
            return true;
        }

        Optional<Charge> maybeCharge = chargeService.findByProviderAndTransactionIdFromDbOrLedger(
                PAYMENT_GATEWAY_NAME, notification.getTransactionId());

        if (maybeCharge.isEmpty()) {
            // Respond with an error, which will cause worldpay to try to send the notification
            // again later — this is necessary because sometimes we might receive a notification
            // for a telephone payment before we know about the payment itself.
            // Note that when a capture notification is rejected, Worldpay retries the notification until it is successful
            // or removed from Worldpay's queue after 7 days. Capture notifications for new payments
            // will be blocked until the notification being retried is successful or removed from Worldpay's queue
            if (gatewayAccountService.isATelephonePaymentNotificationAccount(notification.getMerchantCode())) {
                logger.info("{} notification {} for telephone payments gateway account could not be evaluated (associated charge entity not found)",
                        PAYMENT_GATEWAY_NAME, notification);
                return false;
            }

            logger.info("{} notification {} could not be evaluated (associated charge entity not found)",
                    PAYMENT_GATEWAY_NAME, notification);

            return true;
        }

        Charge charge = maybeCharge.get();
        Optional<GatewayAccountEntity> mayBeGatewayAccountEntity = gatewayAccountService.getGatewayAccount(charge.getGatewayAccountId());

        if (mayBeGatewayAccountEntity.isEmpty()) {
            logger.error("{} notification {} could not be processed (associated gateway account [{}] not found for charge [{}] {}, {})",
                    PAYMENT_GATEWAY_NAME, notification,
                    charge.getGatewayAccountId(),
                    charge.getExternalId(),
                    kv(PAYMENT_EXTERNAL_ID, charge.getExternalId()),
                    kv(GATEWAY_ACCOUNT_ID, charge.getGatewayAccountId()));
            return false;
        }

        GatewayAccountEntity gatewayAccountEntity = mayBeGatewayAccountEntity.get();
        
        if (isCaptureNotification(notification)) {
            if(charge.isHistoric()){
                chargeNotificationProcessor.processCaptureNotificationForExpungedCharge(gatewayAccountEntity, 
                        notification.getTransactionId(), charge, CAPTURED);
                return true;
            }
            chargeNotificationProcessor.invoke(notification.getTransactionId(), charge, CAPTURED, notification.getGatewayEventDate());
        } else if (isRefundNotification(notification)) {
            processRefundNotification(notification, charge, gatewayAccountEntity);
        } else if (isErrorNotification(notification)) {
            if (gatewayAccountEntity.isLive()) {
                logger.error("{} error notification received for live account {}", PAYMENT_GATEWAY_NAME, notification);
            } else {
                logger.info("{} error notification received for test account {}", PAYMENT_GATEWAY_NAME, notification);
            }
        } else {
            logger.error("{} notification {} unknown", PAYMENT_GATEWAY_NAME, notification);
        }
        return true;
    }

    private void processRefundNotification(WorldpayNotification notification, Charge charge, GatewayAccountEntity gatewayAccountEntity) {
        if(isOfflineRefund(notification)) {
            logger.info("{} notification {} ignored (SENT_FOR_REFUND without refund authorisation code)", PAYMENT_GATEWAY_NAME, notification);
        } else {
            refundNotificationProcessor.invoke(PaymentGatewayName.WORLDPAY, newRefundStatus(notification), gatewayAccountEntity,
                notification.getReference(), notification.getTransactionId(), charge);
        }
    }

    private boolean isErrorNotification(WorldpayNotification notification) {
        return "ERROR".equals(notification.getStatus());
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
        return isNotificationEndpointSecured() && !ipDomainMatcher.ipMatchesDomain(ipAddress, notificationDomain());
    }

    private boolean isTransactionIdBlank(WorldpayNotification notification) {
        return isBlank(notification.getTransactionId());
    }

    private boolean isIgnored(WorldpayNotification notification) {
        return IGNORED_STATUSES.contains(notification.getStatus());
    }

    private boolean isOfflineRefund(WorldpayNotification notification) {
        if (!"SENT_FOR_REFUND".equals(notification.getStatus())) {
            return false;
        }
        if (NULL_AUTHORISATION_CODE.equals(notification.getRefundAuthorisationReference())) {
            return true;
        }
        return false;
    }

    public String notificationDomain() {
        return config.getNotificationDomain();
    }

    public Boolean isNotificationEndpointSecured() {
        return config.isNotificationEndpointSecured();
    }
}
