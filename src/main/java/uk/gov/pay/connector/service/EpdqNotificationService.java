package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.provider.ChargeNotificationProcessor;
import uk.gov.pay.connector.provider.RefundNotificationProcessor;
import uk.gov.pay.connector.service.epdq.EpdqNotification;
import uk.gov.pay.connector.service.epdq.EpdqNotification.StatusCode;
import uk.gov.pay.connector.service.epdq.EpdqPaymentProvider;

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.service.StatusFlow.EXPIRE_FLOW;
import static uk.gov.pay.connector.service.StatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.service.StatusFlow.USER_CANCELLATION_FLOW;

public class EpdqNotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeDao chargeDao;
    private final EpdqPaymentProvider paymentProvider;
    private final ChargeNotificationProcessor chargeNotificationProcessor;
    private final RefundNotificationProcessor refundNotificationProcessor;
    private final PaymentGatewayName paymentGatewayName;

    @Inject
    public EpdqNotificationService(ChargeDao chargeDao,
                                   EpdqPaymentProvider paymentProvider,
                                   ChargeNotificationProcessor chargeNotificationProcessor,
                                   RefundNotificationProcessor refundNotificationProcessor) {
        this.chargeDao = chargeDao;
        this.paymentProvider = paymentProvider;
        this.paymentGatewayName = paymentProvider.getPaymentGatewayName();
        this.chargeNotificationProcessor = chargeNotificationProcessor;
        this.refundNotificationProcessor = refundNotificationProcessor;
    }

    @Transactional
    public void handleNotificationFor(String payload) {
        logger.info("Parsing {} notification", providerName());

        EpdqNotification notification;
        try {
            notification = new EpdqNotification(payload);
            logger.info("Parsed {} notification: {}", providerName(), notification.toString());
        }
        catch (EpdqNotification.EpdqParseException e) {
            logger.error("{} notification parsing failed: {}", providerName(), e.toString());
            return;
        }

        logger.info("Verifying {} notification {}", providerName(), notification);

        if (isBlank(notification.getTransactionId())) {
            logger.error("{} notification {} failed verification because it has no transaction ID", providerName(), notification);
            return;
        }

        Optional<ChargeEntity> maybeCharge = chargeDao.findByProviderAndTransactionId(providerName(), notification.getTransactionId());

        if (!maybeCharge.isPresent()) {
            logger.error("{} notification {} could not be verified (associated charge entity not found)",
                    providerName(), notification);
            return;
        }

        ChargeEntity charge = maybeCharge.get();
        if (!paymentProvider.verifyNotification(notification, charge.getGatewayAccount())) {
            logger.error("{} notification {} failed verification", providerName(), notification);
            return;
        }

        logger.info("Evaluating {} notification {}", providerName(), notification);

        final Optional<ChargeStatus> newChargeStatus = newChargeStateForChargeNotification(notification.getStatus(), charge.getChargeStatus());
        final Optional<RefundStatus> newRefundStatus = newRefundStateForRefundNotification(notification.getStatus());

        if (newChargeStatus.isPresent()) {
            chargeNotificationProcessor.invoke(notification.getTransactionId(), charge, newChargeStatus.get(), null);
        } else if (newRefundStatus.isPresent()) {
            refundNotificationProcessor.invoke(
                PaymentGatewayName.EPDQ, newRefundStatus.get(), notification.getReference(), notification.getTransactionId()
            );
        }
    }

    public static Optional<ChargeStatus> newChargeStateForChargeNotification(String notificationStatus, ChargeStatus chargeStatus) {
        final StatusCode statusCode = StatusCode.byCode(notificationStatus);

        switch(statusCode) {
            case EPDQ_AUTHORISATION_REFUSED:
                return Optional.of(AUTHORISATION_REJECTED);
            case EPDQ_AUTHORISED:
                return Optional.of(AUTHORISATION_SUCCESS);

            case EPDQ_AUTHORISED_CANCELLED:
                switch(chargeStatus) {
                    case USER_CANCEL_SUBMITTED:
                        return Optional.of(USER_CANCELLATION_FLOW.getSuccessTerminalState());

                    case EXPIRE_CANCEL_SUBMITTED:
                        return Optional.of(EXPIRE_FLOW.getSuccessTerminalState());

                    case SYSTEM_CANCEL_SUBMITTED:
                    case CREATED:
                    case ENTERING_CARD_DETAILS:
                        return Optional.of(SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState());
                }
                break;

            case EPDQ_PAYMENT_REQUESTED:
                return Optional.of(CAPTURED);
        }
        return Optional.empty();
    }

    public static Optional<RefundStatus> newRefundStateForRefundNotification(String notificationStatus) {
        final StatusCode statusCode = StatusCode.byCode(notificationStatus);

        switch (statusCode) {
            case EPDQ_REFUND:
            case EPDQ_PAYMENT_DELETED:
                return Optional.of(REFUNDED);

            case EPDQ_REFUND_REFUSED:
            case EPDQ_DELETION_REFUSED:
            case EPDQ_REFUND_DECLINED_BY_ACQUIRER:
                return Optional.of(REFUND_ERROR);
        }
        return Optional.empty();
    }

    private String providerName() {
        return paymentGatewayName.getName();
    }
}
