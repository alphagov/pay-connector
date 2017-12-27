package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import org.apache.http.NameValuePair;
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
import uk.gov.pay.connector.service.epdq.SignatureGenerator;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.service.StatusFlow.EXPIRE_FLOW;
import static uk.gov.pay.connector.service.StatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.service.StatusFlow.USER_CANCELLATION_FLOW;
import static uk.gov.pay.connector.service.epdq.EpdqNotification.SHASIGN;

public class EpdqNotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeDao chargeDao;
    private final EpdqPaymentProvider paymentProvider;
    private final SignatureGenerator signatureGenerator;
    private final ChargeNotificationProcessor chargeNotificationProcessor;
    private final RefundNotificationProcessor refundNotificationProcessor;
    private final PaymentGatewayName paymentGatewayName;

    @Inject
    public EpdqNotificationService(ChargeDao chargeDao,
                                   EpdqPaymentProvider paymentProvider,
                                   SignatureGenerator signatureGenerator,
                                   ChargeNotificationProcessor chargeNotificationProcessor,
                                   RefundNotificationProcessor refundNotificationProcessor) {
        this.chargeDao = chargeDao;
        this.paymentProvider = paymentProvider;
        this.paymentGatewayName = paymentProvider.getPaymentGatewayName();
        this.signatureGenerator = signatureGenerator;
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
        if (!verifyNotificationSignature(notification, charge)) {
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

    private boolean verifyNotificationSignature(EpdqNotification notification, ChargeEntity charge) {
        String actualSignature = signatureGenerator.sign(
                getParams(notification, false),
                getShaOutPassphrase(charge)
        );

        final String expectedShaSignature = getExpectedShaSignature(notification);
        final boolean verified = actualSignature.equalsIgnoreCase(expectedShaSignature);
        if (!verified) {
            logger.error("{} notification {} failed verification. Actual signature [{}] expected [{}]",
                    providerName(), notification, actualSignature, expectedShaSignature);
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
                .collect(Collectors.partitioningBy(p -> p.getName().equalsIgnoreCase(SHASIGN)))
                .get(withShaSignature);
    }

    private String getShaOutPassphrase(ChargeEntity charge) {
        return charge.getGatewayAccount().getCredentials().get(CREDENTIALS_SHA_OUT_PASSPHRASE);
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
