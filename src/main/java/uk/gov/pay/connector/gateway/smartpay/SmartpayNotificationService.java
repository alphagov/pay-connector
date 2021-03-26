package uk.gov.pay.connector.gateway.smartpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.name.Named;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.MappedChargeStatus;
import uk.gov.pay.connector.gateway.model.status.MappedRefundStatus;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.util.IpAddressMatcher;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class SmartpayNotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeService chargeService;
    private final GatewayAccountService gatewayAccountService;
    private final ChargeNotificationProcessor chargeNotificationProcessor;
    private final RefundNotificationProcessor refundNotificationProcessor;
    private final IpAddressMatcher ipAddressMatcher;
    private final Set<String> allowedSmartpayIpAddresses;
    private final ObjectMapper objectMapper;

    private static final String PAYMENT_GATEWAY_NAME = SMARTPAY.getName();

    @Inject
    public SmartpayNotificationService(ChargeService chargeService,
                                       ChargeNotificationProcessor chargeNotificationProcessor,
                                       RefundNotificationProcessor refundNotificationProcessor,
                                       GatewayAccountService gatewayAccountService,
                                       IpAddressMatcher ipAddressMatcher,
                                       @Named("AllowedSmartpayIpAddresses") Set<String> allowedSmartpayIpAddresses, 
                                       ObjectMapper objectMapper) {
        this.chargeService = chargeService;
        this.chargeNotificationProcessor = chargeNotificationProcessor;
        this.refundNotificationProcessor = refundNotificationProcessor;
        this.gatewayAccountService = gatewayAccountService;
        this.ipAddressMatcher = ipAddressMatcher;
        this.allowedSmartpayIpAddresses = allowedSmartpayIpAddresses;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean handleNotificationFor(String payload, String forwardedIpAddresses) {
        if (!ipAddressMatcher.isMatch(forwardedIpAddresses, allowedSmartpayIpAddresses)) {
            return false;
        }

        List<SmartpayNotification> notifications = parse(payload);
        for (SmartpayNotification notification : notifications) {
            handle(notification);
        }
        return true;
    }

    private void handle(SmartpayNotification notification) {
        if (shouldIgnore(notification)) {
            logger.info("{} notification {} ignored", PAYMENT_GATEWAY_NAME, notification);
            return;
        }

        logger.info("Verifying {} notification {}", PAYMENT_GATEWAY_NAME, notification);

        if (isBlank(notification.getTransactionId())) {
            logger.error("{} notification {} failed verification because it has no transaction ID", PAYMENT_GATEWAY_NAME, notification);
            return;
        }

        logger.info("Evaluating {} notification {}", PAYMENT_GATEWAY_NAME, notification);

        Optional<Charge> maybeCharge = chargeService.findByProviderAndTransactionIdFromDbOrLedger(PAYMENT_GATEWAY_NAME,
                notification.getOriginalReference());

        if (maybeCharge.isEmpty()) {
            logger.warn("{} notification {} could not be evaluated (associated charge entity not found)",
                    PAYMENT_GATEWAY_NAME, notification);
            return;
        }

        Charge charge = maybeCharge.get();

        Optional<GatewayAccountEntity> mayBeGatewayAccountEntity =
                gatewayAccountService.getGatewayAccount(charge.getGatewayAccountId());

        if (mayBeGatewayAccountEntity.isEmpty()) {
            logger.error("{} notification {} could not be processed (associated gateway account [{}] not found for charge [{}] {}, {})",
                    PAYMENT_GATEWAY_NAME, notification,
                    charge.getGatewayAccountId(),
                    charge.getExternalId(),
                    kv(PAYMENT_EXTERNAL_ID, charge.getExternalId()),
                    kv(GATEWAY_ACCOUNT_ID, charge.getGatewayAccountId()));
            return;
        }
        GatewayAccountEntity gatewayAccountEntity = mayBeGatewayAccountEntity.get();

        InterpretedStatus interpretedStatus = interpretStatus(notification);

        if (interpretedStatus instanceof MappedChargeStatus) {
            if(charge.isHistoric()){
                logger.error("{} notification {} could not be processed as charge [{}] has been expunged from connector {} {}",
                        PAYMENT_GATEWAY_NAME, notification,
                        charge.getExternalId(),
                        kv(PAYMENT_EXTERNAL_ID, charge.getExternalId()),
                        kv(GATEWAY_ACCOUNT_ID, charge.getGatewayAccountId()));
                return;
            }
            chargeNotificationProcessor.invoke(
                    notification.getOriginalReference(),
                    charge,
                    interpretedStatus.getChargeStatus(),
                    notification.getEventDate()
            );
        } else if (interpretedStatus instanceof MappedRefundStatus) {
            refundNotificationProcessor.invoke(
                    SMARTPAY,
                    interpretedStatus.getRefundStatus(),
                    gatewayAccountEntity,
                    notification.getPspReference(),
                    notification.getOriginalReference(),
                    charge
            );
        } else {
            logger.error("{} notification {} unknown", PAYMENT_GATEWAY_NAME, notification);
        }
    }

    private InterpretedStatus interpretStatus(SmartpayNotification notification) {
        return SmartpayStatusMapper.from(notification.getStatus());
    }

    private boolean shouldIgnore(SmartpayNotification notification) {
        return SmartpayStatusMapper.from(notification.getStatus()).getType() 
                == InterpretedStatus.Type.IGNORED;
    }

    private InterpretedStatus interpretedStatusFrom(SmartpayStatus status) {
        return SmartpayStatusMapper.from(status);
    }

    private List<SmartpayNotification> parse(String payload) {
        logger.info("Parsing {} notification", PAYMENT_GATEWAY_NAME);

        List<SmartpayNotification> result;
        try {
            result = parseNotification(payload);
            logger.info("Parsed {} notification: {}", PAYMENT_GATEWAY_NAME, result);
        } catch (SmartpayParseException e) {
            logger.error("{} notification parsing failed: {}", PAYMENT_GATEWAY_NAME, e);
            result = Collections.emptyList();
        }
        return result;
    }

    private List<SmartpayNotification> parseNotification(String payload) throws SmartpayParseException {
        try {
            // TODO for authorisation notifications, this does the wrong thing
            // Transaction ID is pspReference, not originalReference as the code below assumes
            // https://www.barclaycard.co.uk/business/files/SmartPay_Notifications_Guide.pdf
            // We will set the transaction ID to blank, which makes the notification effectively useless
            // This is OK at the moment because we ignore authorisation notifications for Smartpay
            return objectMapper.readValue(payload, SmartpayNotificationList.class).getNotifications();
        } catch (Exception e) {
            throw new SmartpayParseException(e);
        }
    }
}
