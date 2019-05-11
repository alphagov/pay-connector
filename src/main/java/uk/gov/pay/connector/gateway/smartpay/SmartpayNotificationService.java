package uk.gov.pay.connector.gateway.smartpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.MappedChargeStatus;
import uk.gov.pay.connector.gateway.model.status.MappedRefundStatus;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;

public class SmartpayNotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeDao chargeDao;
    private final ChargeNotificationProcessor chargeNotificationProcessor;
    private final RefundNotificationProcessor refundNotificationProcessor;

    private static final String PAYMENT_GATEWAY_NAME = SMARTPAY.getName();

    @Inject
    public SmartpayNotificationService(ChargeDao chargeDao,
                                       ChargeNotificationProcessor chargeNotificationProcessor,
                                       RefundNotificationProcessor refundNotificationProcessor) {
        this.chargeDao = chargeDao;
        this.chargeNotificationProcessor = chargeNotificationProcessor;
        this.refundNotificationProcessor = refundNotificationProcessor;
    }

    @Transactional
    public boolean handleNotificationFor(String payload) {
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

        Optional<ChargeEntity> maybeCharge = chargeDao.findByProviderAndTransactionId(PAYMENT_GATEWAY_NAME,
                notification.getOriginalReference());

        if (maybeCharge.isEmpty()) {
            logger.error("{} notification {} could not be evaluated (associated charge entity not found)",
                    PAYMENT_GATEWAY_NAME, notification);
            return;
        }

        ChargeEntity charge = maybeCharge.get();
        InterpretedStatus interpretedStatus = interpretStatus(notification, charge);

        if (interpretedStatus instanceof MappedChargeStatus) {
            chargeNotificationProcessor.invoke(
                    notification.getPspReference(),
                    charge,
                    interpretedStatus.getChargeStatus(),
                    notification.getEventDate()
            );
        } else if (interpretedStatus instanceof MappedRefundStatus) {
            refundNotificationProcessor.invoke(
                    SMARTPAY,
                    interpretedStatus.getRefundStatus(),
                    notification.getPspReference(),
                    notification.getOriginalReference()
            );
        } else {
            logger.error("{} notification {} unknown", PAYMENT_GATEWAY_NAME, notification);
        }
    }

    private InterpretedStatus interpretStatus(SmartpayNotification notification, ChargeEntity charge) {
        return SmartpayStatusMapper.from(
                notification.getStatus(),
                ChargeStatus.fromString(charge.getStatus())
        );
    }

    private boolean shouldIgnore(SmartpayNotification notification) {
        return interpretedStatusFrom(notification.getStatus())
                .getType() == InterpretedStatus.Type.IGNORED;
    }

    private InterpretedStatus interpretedStatusFrom(Pair<String, Boolean> status) {
        return SmartpayStatusMapper
                .from(status);
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
            ObjectMapper objectMapper = new ObjectMapper();
            // TODO for authorisation notifications, this does the wrong thing
            // Transaction ID is pspReference, not originalReference as the code below assumes
            // https://www.barclaycard.co.uk/business/files/SmartPay_Notifications_Guide.pdf
            // We will set the transaction ID to blank, which makes the notification effectively useless
            // This is OK at the moment because we ignore authorisation notifications for Smartpay
            return objectMapper.readValue(payload, SmartpayNotificationList.class)
                    .getNotifications();
        } catch (Exception e) {
            throw new SmartpayParseException(e);
        }
    }
}
