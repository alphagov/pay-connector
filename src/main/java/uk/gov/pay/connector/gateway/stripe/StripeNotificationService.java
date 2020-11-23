package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.events.model.payout.PayoutEvent;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.stripe.json.StripePaymentIntent;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;
import uk.gov.pay.connector.gateway.stripe.json.StripeSourcesResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;
import uk.gov.pay.connector.payout.PayoutEmitterService;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.payout.Payout;
import uk.gov.pay.connector.queue.payout.PayoutReconcileQueue;
import uk.gov.pay.connector.util.IpAddressMatcher;

import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.ACCOUNT_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_PAYMENT_FAILED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYOUT_CREATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYOUT_FAILED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYOUT_PAID;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYOUT_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CANCELED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CHARGEABLE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_FAILED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.byType;
import static uk.gov.pay.logging.LoggingKeys.CONNECT_ACCOUNT_ID;
import static uk.gov.pay.logging.LoggingKeys.GATEWAY_PAYOUT_ID;
import static uk.gov.pay.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class StripeNotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<StripeNotificationType> sourceTypes = List.of(
            SOURCE_CANCELED,
            SOURCE_CHARGEABLE,
            SOURCE_FAILED
    );

    private final List<StripeNotificationType> paymentIntentTypes = List.of(
            PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED,
            PAYMENT_INTENT_PAYMENT_FAILED
    );

    private final List<StripeNotificationType> payoutTypes = List.of(
            PAYOUT_CREATED, PAYOUT_UPDATED, PAYOUT_FAILED, PAYOUT_PAID
    );

    private final List<ChargeStatus> threeDSAuthorisableStates = List.of(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_3DS_READY);

    private final ChargeService chargeService;
    private final Card3dsResponseAuthService card3dsResponseAuthService;
    private final ObjectMapper objectMapper;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final StripeAccountUpdatedHandler stripeAccountUpdatedHandler;
    private final PayoutReconcileQueue payoutReconcileQueue;
    private final PayoutEmitterService payoutEmitterService;
    private final IpAddressMatcher ipAddressMatcher;
    private final Set<String> allowedStripeIpAddresses;

    private static final String PAYMENT_GATEWAY_NAME = PaymentGatewayName.STRIPE.getName();
    private static final long DEFAULT_TOLERANCE = 300L;

    @Inject
    public StripeNotificationService(Card3dsResponseAuthService card3dsResponseAuthService,
                                     ChargeService chargeService,
                                     StripeGatewayConfig stripeGatewayConfig,
                                     StripeAccountUpdatedHandler stripeAccountUpdatedHandler,
                                     PayoutReconcileQueue payoutReconcileQueue,
                                     PayoutEmitterService payoutEmitterService,
                                     IpAddressMatcher ipAddressMatcher,
                                     @Named("AllowedStripeIpAddresses") Set<String> allowedStripeIpAddresses) {
        this.card3dsResponseAuthService = card3dsResponseAuthService;
        this.chargeService = chargeService;
        this.stripeAccountUpdatedHandler = stripeAccountUpdatedHandler;
        this.payoutReconcileQueue = payoutReconcileQueue;
        objectMapper = new ObjectMapper();
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.payoutEmitterService = payoutEmitterService;
        this.ipAddressMatcher = ipAddressMatcher;
        this.allowedStripeIpAddresses = allowedStripeIpAddresses;
    }

    public boolean handleNotificationFor(String payload, String signatureHeader, String forwardedIpAddresses) {
        if (!ipAddressMatcher.isMatch(forwardedIpAddresses, allowedStripeIpAddresses)) {
            return false;
        }

        logger.info("Parsing {} notification", PAYMENT_GATEWAY_NAME);

        if (!isValidNotificationSignature(payload, signatureHeader)) {
            throw new WebApplicationException(format("Invalid notification signature from %s [%s]", PAYMENT_GATEWAY_NAME, signatureHeader));
        }

        StripeNotification notification;
        try {
            notification = parseNotification(payload);
            logger.info("Parsed {} notification: {}", PAYMENT_GATEWAY_NAME, notification);
        } catch (StripeParseException e) {
            logger.error("{} notification parsing failed: {}", PAYMENT_GATEWAY_NAME, e);
            return true;
        }

        if (isASourceNotification(notification)) {
            processSourceNotification(notification);
        } else if (isAPaymentIntentNotification(notification)) {
            processPaymentIntentNotification(notification);
        } else if (isAnAccountUpdatedNotification(notification)) {
            stripeAccountUpdatedHandler.process(notification);
        } else if (isAPayoutNotification(notification)) {
            processPayoutNotification(notification);
        }
        return true;
    }

    private boolean isAnAccountUpdatedNotification(StripeNotification notification) {
        return byType(notification.getType()) == ACCOUNT_UPDATED;
    }

    private void processPayoutNotification(StripeNotification notification) {
        logger.info(format("Processing %s payout created notification with id [%s]", PAYMENT_GATEWAY_NAME,
                notification.getId()),
                kv(CONNECT_ACCOUNT_ID, notification.getAccount()));
        try {
            StripePayout stripePayout = toPayout(notification.getObject());
            StripeNotificationType stripeNotificationType = byType(notification.getType());

            if (PAYOUT_CREATED.equals(stripeNotificationType)) {
                Payout payout = new Payout(stripePayout.getId(), notification.getAccount(), stripePayout.getCreated());
                sendToPayoutReconcileQueue(notification.getAccount(), payout);
            } else {
                Optional<Class<? extends PayoutEvent>> mayBeEventClass = stripeNotificationType.getEventClass();

                if (mayBeEventClass.isEmpty()) {
                    logger.warn(format("Event class is not assigned for Stripe payout type [%s]",
                            notification.getType()),
                            kv(GATEWAY_PAYOUT_ID, stripePayout.getId()),
                            kv(CONNECT_ACCOUNT_ID, notification.getAccount()));
                } else {
                    payoutEmitterService.emitPayoutEvent(mayBeEventClass.get(), notification.getCreated(),
                            notification.getAccount(), stripePayout);
                }
            }
        } catch (StripeParseException e) {
            logger.error(format("%s payout notification parsing failed for notification with id [%s]: %s",
                    PAYMENT_GATEWAY_NAME, notification.getId(), e),
                    kv(CONNECT_ACCOUNT_ID, notification.getAccount()));
        }
    }

    private void sendToPayoutReconcileQueue(String connectAccount, Payout payout) {
        try {
            payoutReconcileQueue.sendPayout(payout);
        } catch (QueueException | JsonProcessingException e) {
            logger.error(format("Error sending payout to payout reconcile queue: exception [%s]", e.getMessage()),
                    kv(GATEWAY_PAYOUT_ID, payout.getGatewayPayoutId()),
                    kv(CONNECT_ACCOUNT_ID, connectAccount));
        }
    }

    private void processPaymentIntentNotification(StripeNotification notification) {
        try {
            StripePaymentIntent paymentIntent = toPaymentIntent(notification.getObject());

            if (isBlank(paymentIntent.getId())) {
                logger.warn("{} payment intent notification [{}] failed verification because it has no transaction ID", PAYMENT_GATEWAY_NAME, notification);
                return;
            }

            Optional<ChargeEntity> maybeCharge = chargeService.findByProviderAndTransactionId(PAYMENT_GATEWAY_NAME, paymentIntent.getId());

            if (maybeCharge.isEmpty()) {
                logger.info("{} notification for payment intent [{}] could not be verified (associated charge entity not found)",
                        PAYMENT_GATEWAY_NAME, paymentIntent.getId());
                return;
            }

            ChargeEntity charge = maybeCharge.get();

            if (PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED.getType().equals(notification.getType()) &&
                    !paymentIntent.getAmountCapturable().equals(charge.getAmount())) {
                logger.error("{} notification for payment intent [{}] does not have amount capturable equal to original charge {}",
                        PAYMENT_GATEWAY_NAME, paymentIntent.getId(), charge.getExternalId());
                return;
            }

            if (isChargeIn3DSRequiredOrReadyState(ChargeStatus.fromString(charge.getStatus()))) {
                executePost3DSAuthorisation(charge, notification.getType());
            }

        } catch (StripeParseException e) {
            logger.error("{} notification parsing for payment intent object failed: {}", PAYMENT_GATEWAY_NAME, e);
        }
    }

    private StripePaymentIntent toPaymentIntent(String payload) throws StripeParseException {
        try {
            return objectMapper.readValue(payload, StripePaymentIntent.class);
        } catch (Exception e) {
            throw new StripeParseException(e.getMessage());
        }
    }

    private StripePayout toPayout(String payload) throws StripeParseException {
        try {
            return objectMapper.readValue(payload, StripePayout.class);
        } catch (Exception e) {
            throw new StripeParseException(e.getMessage());
        }
    }

    private void processSourceNotification(StripeNotification notification) {
        try {
            StripeSourcesResponse stripeSourcesResponse = toSourceObject(notification.getObject());

            if (isBlank(stripeSourcesResponse.getTransactionId())) {
                logger.error("{} source notification [{}] failed verification because it has no transaction ID", PAYMENT_GATEWAY_NAME, notification);
                return;
            }

            logger.info("Evaluating {} source notification [notification id - {}, source id - {}]",
                    PAYMENT_GATEWAY_NAME,
                    notification.getId(),
                    stripeSourcesResponse.getTransactionId()
            );

            Optional<ChargeEntity> maybeCharge = chargeService.findByProviderAndTransactionId(PAYMENT_GATEWAY_NAME, stripeSourcesResponse.getTransactionId());

            if (maybeCharge.isEmpty()) {
                logger.error("{} notification for source [{}] could not be verified (associated charge entity not found)",
                        PAYMENT_GATEWAY_NAME, stripeSourcesResponse.getTransactionId());
                return;
            }

            ChargeEntity charge = maybeCharge.get();

            if (isChargeIn3DSRequiredOrReadyState(ChargeStatus.fromString(charge.getStatus()))) {
                executePost3DSAuthorisation(charge, notification.getType());
            }

        } catch (StripeParseException e) {
            logger.error("{} notification parsing for source object failed: {}", PAYMENT_GATEWAY_NAME, e);
        }
    }

    private void executePost3DSAuthorisation(ChargeEntity charge, String notificationEventType) {
        try {
            final StripeNotificationType type = byType(notificationEventType);

            Auth3dsResult auth3DsResult = new Auth3dsResult();
            auth3DsResult.setAuth3dsResult(getMappedAuth3dsResult(type));

            delayFor3dsReady(charge);
            card3dsResponseAuthService.process3DSecureAuthorisationWithoutLocking(charge.getExternalId(), auth3DsResult);
        } catch (OperationAlreadyInProgressRuntimeException e) {
            // CardExecutorService is asynchronous and sends back 'OperationAlreadyInProgressRuntimeException' 
            // exception while the charge is being authorised. Catch this exception to send a response with 
            // http status 200 instead of depending on the status returned by Exception 
        }
    }

    private void delayFor3dsReady(ChargeEntity charge) {
        int totalTimeDelayedInMillis = 0;
        int delayInMillis = 200;
        while (totalTimeDelayedInMillis < stripeGatewayConfig.getNotification3dsWaitDelay()) {
            ChargeEntity chargeEntity = chargeService.findChargeByExternalId(charge.getExternalId());
            if (ChargeStatus.fromString(chargeEntity.getStatus()) == AUTHORISATION_3DS_READY) {
                break;
            }
            try {
                Thread.sleep(delayInMillis);
            } catch (InterruptedException e) {
                logger.error("Waiting for 3ds ready locking state failed, {}", kv("error", e.getMessage()));
            }
            totalTimeDelayedInMillis += delayInMillis;
        }
        logger.info("Total time waited for Frontend to update charge [{}] to 3ds ready - {} milliseconds,"
                , kv(PAYMENT_EXTERNAL_ID, charge.getExternalId()), totalTimeDelayedInMillis);
    }

    private boolean isASourceNotification(StripeNotification notification) {
        return sourceTypes.contains(byType(notification.getType()));
    }

    private boolean isAPaymentIntentNotification(StripeNotification notification) {
        return paymentIntentTypes.contains(byType(notification.getType()));
    }

    private boolean isAPayoutNotification(StripeNotification notification) {
        return payoutTypes.contains(byType(notification.getType()));
    }

    private String getMappedAuth3dsResult(StripeNotificationType type) {
        switch (type) {
            case SOURCE_CHARGEABLE:
            case PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED:
                return Auth3dsResult.Auth3dsResultOutcome.AUTHORISED.toString();
            case SOURCE_CANCELED:
                return Auth3dsResult.Auth3dsResultOutcome.CANCELED.toString();
            case SOURCE_FAILED:
            case PAYMENT_INTENT_PAYMENT_FAILED:
                return Auth3dsResult.Auth3dsResultOutcome.DECLINED.toString();
            default:
                return Auth3dsResult.Auth3dsResultOutcome.ERROR.toString();
        }
    }

    private boolean isChargeIn3DSRequiredOrReadyState(ChargeStatus chargeStatus) {
        return threeDSAuthorisableStates.contains(chargeStatus);
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

    private boolean isValidNotificationSignature(String payload, String signatureHeader) {
        boolean isValid = stripeGatewayConfig.getWebhookSigningSecrets()
                .stream()
                .anyMatch(s -> isValidNotificationSignature(payload, signatureHeader, s));
        if (isValid) {
            return true;
        } else {
            logger.warn("Could not verify Stripe authentication header");
            return false;
        }
    }

    private boolean isValidNotificationSignature(String payload, String signatureHeader, String secret) {
        try {
            return Webhook.Signature.verifyHeader(payload,
                    signatureHeader,
                    secret,
                    DEFAULT_TOLERANCE);
        } catch (SignatureVerificationException e) {
            return false;
        }
    }
}
