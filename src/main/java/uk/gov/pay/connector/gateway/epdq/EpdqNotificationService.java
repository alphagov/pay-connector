package uk.gov.pay.connector.gateway.epdq;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.apache.http.NameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.service.StatusFlow.EXPIRE_FLOW;
import static uk.gov.pay.connector.charge.service.StatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.charge.service.StatusFlow.USER_CANCELLATION_FLOW;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.SHASIGN_KEY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;

public class EpdqNotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeDao chargeDao;
    private final SignatureGenerator signatureGenerator;
    private final ChargeNotificationProcessor chargeNotificationProcessor;
    private final RefundNotificationProcessor refundNotificationProcessor;

    private static final String PAYMENT_GATEWAY_NAME = PaymentGatewayName.EPDQ.getName();

    @Inject
    public EpdqNotificationService(ChargeDao chargeDao,
                                   SignatureGenerator signatureGenerator,
                                   ChargeNotificationProcessor chargeNotificationProcessor,
                                   RefundNotificationProcessor refundNotificationProcessor) {
        this.chargeDao = chargeDao;
        this.signatureGenerator = signatureGenerator;
        this.chargeNotificationProcessor = chargeNotificationProcessor;
        this.refundNotificationProcessor = refundNotificationProcessor;
    }

    @Transactional
    public void handleNotificationFor(String payload) {
        logger.info("Parsing {} notification", PAYMENT_GATEWAY_NAME);

        EpdqNotification notification;
        try {
            notification = new EpdqNotification(payload);
            logger.info("Parsed {} notification: {}", PAYMENT_GATEWAY_NAME, notification);
        } catch (EpdqParseException e) {
            logger.error("{} notification parsing failed: {}", PAYMENT_GATEWAY_NAME, e);
            return;
        }

        logger.info("Verifying {} notification {}", PAYMENT_GATEWAY_NAME, notification);

        if (isBlank(notification.getTransactionId())) {
            logger.error("{} notification {} failed verification because it has no transaction ID", PAYMENT_GATEWAY_NAME, notification);
            return;
        }

        Optional<ChargeEntity> maybeCharge = chargeDao.findByProviderAndTransactionId(PAYMENT_GATEWAY_NAME, notification.getTransactionId());

        if (!maybeCharge.isPresent()) {
            logger.error("{} notification {} could not be verified (associated charge entity not found)",
                    PAYMENT_GATEWAY_NAME, notification);
            return;
        }

        ChargeEntity charge = maybeCharge.get();
        if (!isValidNotificationSignature(notification, charge)) {
            return;
        }

        logger.info("Evaluating {} notification {}", PAYMENT_GATEWAY_NAME, notification);

        final Optional<ChargeStatus> newChargeStatus = newChargeStateForChargeNotification(notification.getStatus(), ChargeStatus.fromString(charge.getStatus()));

        if (newChargeStatus.isPresent()) {
            chargeNotificationProcessor.invoke(notification.getTransactionId(), charge, newChargeStatus.get(), null);
        } else {
            final Optional<RefundStatus> newRefundStatus = newRefundStateForRefundNotification(notification.getStatus());
            newRefundStatus.ifPresent(refundStatus -> refundNotificationProcessor.invoke(
                    PaymentGatewayName.EPDQ, refundStatus, notification.getReference(), notification.getTransactionId()
            ));
        }
    }

    private boolean isValidNotificationSignature(EpdqNotification notification, ChargeEntity charge) {
        String actualSignature = signatureGenerator.sign(
                getParams(notification, false),
                getShaOutPassphrase(charge)
        );

        final String expectedShaSignature = getExpectedShaSignature(notification);
        final boolean verified = actualSignature.equalsIgnoreCase(expectedShaSignature);
        if (!verified) {
            logger.error("{} notification {} failed verification. Actual signature [{}] expected [{}]",
                    PAYMENT_GATEWAY_NAME, notification, actualSignature, expectedShaSignature);
        }
        return verified;
    }

    private String getExpectedShaSignature(EpdqNotification notification) {
        try {
            return getParams(notification, true).get(0).getValue();
        } catch (IndexOutOfBoundsException e) {
            return "";
        }
    }

    private List<NameValuePair> getParams(EpdqNotification notification, boolean withShaSignature) {
        return notification.getParams()
                .stream()
                .collect(Collectors.partitioningBy(p -> p.getName().equalsIgnoreCase(SHASIGN_KEY)))
                .get(withShaSignature);
    }

    private String getShaOutPassphrase(ChargeEntity charge) {
        return charge.getGatewayAccount().getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE);
    }

    private static Optional<ChargeStatus> newChargeStateForChargeNotification(String notificationStatus, ChargeStatus chargeStatus) {
        final EpdqNotification.StatusCode statusCode = EpdqNotification.StatusCode.byCode(notificationStatus);

        switch (statusCode) {
            case EPDQ_AUTHORISATION_REFUSED:
                return Optional.of(AUTHORISATION_REJECTED);
            case EPDQ_AUTHORISED:
                return Optional.of(AUTHORISATION_SUCCESS);

            case EPDQ_AUTHORISED_CANCELLED:
                switch (chargeStatus) {
                    case USER_CANCEL_SUBMITTED:
                        return Optional.of(USER_CANCELLATION_FLOW.getSuccessTerminalState());

                    case EXPIRE_CANCEL_SUBMITTED:
                        return Optional.of(EXPIRE_FLOW.getSuccessTerminalState());

                    case SYSTEM_CANCEL_SUBMITTED:
                    case CREATED:
                    case ENTERING_CARD_DETAILS:
                        return Optional.of(SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState());
                    default:
                        return Optional.empty();
                }
            case EPDQ_PAYMENT_REQUESTED:
                return Optional.of(CAPTURED);
            default:
                return Optional.empty();
        }
    }

    private static Optional<RefundStatus> newRefundStateForRefundNotification(String notificationStatus) {
        final EpdqNotification.StatusCode statusCode = EpdqNotification.StatusCode.byCode(notificationStatus);

        switch (statusCode) {
            case EPDQ_REFUND:
            case EPDQ_PAYMENT_DELETED:
                return Optional.of(REFUNDED);

            case EPDQ_REFUND_REFUSED:
            case EPDQ_DELETION_REFUSED:
            case EPDQ_REFUND_DECLINED_BY_ACQUIRER:
                return Optional.of(REFUND_ERROR);
            default:
                return Optional.empty();
        }
    }
}
