package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.stripe.json.StripeSourcesResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;

import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CANCELED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CHARGEABLE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_FAILED;

public class StripeNotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<StripeNotificationType> sourceTypes = ImmutableList.of(SOURCE_CANCELED, SOURCE_CHARGEABLE, SOURCE_FAILED);

    private final ChargeDao chargeDao;
    private final ChargeNotificationProcessor chargeNotificationProcessor;
    private final ObjectMapper objectMapper;

    private static final String PAYMENT_GATEWAY_NAME = PaymentGatewayName.STRIPE.getName();

    @Inject
    public StripeNotificationService(ChargeDao chargeDao,
                                     ChargeNotificationProcessor chargeNotificationProcessor) {
        this.chargeDao = chargeDao;
        this.chargeNotificationProcessor = chargeNotificationProcessor;
        objectMapper = new ObjectMapper();
    }

    @Transactional
    public void handleNotificationFor(String payload) {
        logger.info("Parsing {} notification", PAYMENT_GATEWAY_NAME);

        StripeNotification notification;
        try {
            notification = parseNotification(payload);
            logger.info("Parsed {} notification: {}", PAYMENT_GATEWAY_NAME, notification);
        } catch (StripeParseException e) {
            logger.error("{} notification parsing failed: {}", PAYMENT_GATEWAY_NAME, e);
            return;
        }

        if (isASourceNotification(notification)) {
            processSourceNotification(notification);
        }
    }

    private void processSourceNotification(StripeNotification notification) {
        try {
            StripeSourcesResponse stripeSourcesResponse = toSourceObject(notification.getObject());

            if (isBlank(stripeSourcesResponse.getTransactionId())) {
                logger.error("{} notification {} failed verification because it has no transaction ID", PAYMENT_GATEWAY_NAME, notification);
                return;
            }

            logger.info("Evaluating {} for source notification [{}]", PAYMENT_GATEWAY_NAME, stripeSourcesResponse.getTransactionId());

            Optional<ChargeEntity> maybeCharge = chargeDao.findByProviderAndTransactionId(PAYMENT_GATEWAY_NAME, stripeSourcesResponse.getTransactionId());

            if (!maybeCharge.isPresent()) {
                logger.error("{} notification for source [{}] could not be verified (associated charge entity not found)",
                        PAYMENT_GATEWAY_NAME, stripeSourcesResponse.getTransactionId());
                return;
            }

            ChargeEntity charge = maybeCharge.get();

            final Optional<ChargeStatus> newChargeStatus = newChargeStateForSourceNotification(notification.getType(), ChargeStatus.fromString(charge.getStatus()));

            if (newChargeStatus.isPresent()) {
                chargeNotificationProcessor.invoke(stripeSourcesResponse.getTransactionId(), charge, newChargeStatus.get(), null);
            }
        } catch (StripeParseException e) {
            logger.error("{} notification parsing for source object failed: {}", PAYMENT_GATEWAY_NAME, e);
        }
    }

    private boolean isASourceNotification(StripeNotification notification) {
        return sourceTypes.contains(StripeNotificationType.byType(notification.getType()));
    }

    private static Optional<ChargeStatus> newChargeStateForSourceNotification(String notificationEventType, ChargeStatus chargeStatus) {
        final StripeNotificationType type = StripeNotificationType.byType(notificationEventType);

        if (AUTHORISATION_3DS_REQUIRED.equals(chargeStatus)) {
            switch (type) {
                case SOURCE_CHARGEABLE:
                    return Optional.of(AUTHORISATION_3DS_READY);
                case SOURCE_CANCELED:
                    return Optional.of(AUTHORISATION_CANCELLED);
                case SOURCE_FAILED:
                    return Optional.of(AUTHORISATION_REJECTED);
                default:
                    return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private StripeNotification parseNotification(String payload) throws StripeParseException {
        try {
            return objectMapper.readValue(payload, StripeNotification.class);
        } catch (Exception e) {
            throw new StripeParseException(e.getMessage());
        }
    }

    private StripeSourcesResponse toSourceObject(String payload) throws StripeParseException {
        try {
            return objectMapper.readValue(payload, StripeSourcesResponse.class);
        } catch (Exception e) {
            throw new StripeParseException(e.getMessage());
        }
    }
}
