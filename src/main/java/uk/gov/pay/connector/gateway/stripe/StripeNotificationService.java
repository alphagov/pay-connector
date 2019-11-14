package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.stripe.json.StripePaymentIntent;
import uk.gov.pay.connector.gateway.stripe.json.StripeSourcesResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;

import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.ACCOUNT_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYMENT_INTENT_PAYMENT_FAILED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CANCELED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CHARGEABLE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_FAILED;

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
    
    private final List<ChargeStatus> threeDSAuthorisableStates = List.of(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_3DS_READY);

    private final ChargeService chargeService;
    private final Card3dsResponseAuthService card3dsResponseAuthService;
    private final ObjectMapper objectMapper;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final StripeAccountUpdatedHandler stripeAccountUpdatedHandler;

    private static final String PAYMENT_GATEWAY_NAME = PaymentGatewayName.STRIPE.getName();
    private static final long DEFAULT_TOLERANCE = 300L;

    @Inject
    public StripeNotificationService(Card3dsResponseAuthService card3dsResponseAuthService,
                                     ChargeService chargeService,
                                     StripeGatewayConfig stripeGatewayConfig, 
                                     StripeAccountUpdatedHandler stripeAccountUpdatedHandler) {
        this.card3dsResponseAuthService = card3dsResponseAuthService;
        this.chargeService = chargeService;
        this.stripeAccountUpdatedHandler = stripeAccountUpdatedHandler;
        objectMapper = new ObjectMapper();
        this.stripeGatewayConfig = stripeGatewayConfig;
    }

    public void handleNotificationFor(String payload, String signatureHeader) {
        logger.info("Parsing {} notification", PAYMENT_GATEWAY_NAME);

        if (!isValidNotificationSignature(payload, signatureHeader)) {
            throw new WebApplicationException(String.format("Invalid notification signature from %s [%s]", PAYMENT_GATEWAY_NAME, signatureHeader));
        }

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
        } else if (isAPaymentIntentNotification(notification)) {
            processPaymentIntentNotification(notification);
        } else if (isAnAccountUpdatedNotification(notification)) {
            stripeAccountUpdatedHandler.process(notification);
        }
    }

    private boolean isAnAccountUpdatedNotification(StripeNotification notification) {
        return StripeNotificationType.byType(notification.getType()) == ACCOUNT_UPDATED;
    }

    private void processPaymentIntentNotification(StripeNotification notification) {
        try {
            StripePaymentIntent paymentIntent = toPaymentIntent(notification.getObject());

            if (isBlank(paymentIntent.getId())) {
                logger.warn("{} payment intent notification [{}] failed verification because it has no transaction ID", PAYMENT_GATEWAY_NAME, notification);
                return;
            }
            
            Optional<ChargeEntity> maybeCharge = chargeService.findByProviderAndTransactionId(PAYMENT_GATEWAY_NAME, paymentIntent.getId());

            if (!maybeCharge.isPresent()) {
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

            if (!maybeCharge.isPresent()) {
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
            final StripeNotificationType type = StripeNotificationType.byType(notificationEventType);

            Auth3dsDetails auth3dsDetails = new Auth3dsDetails();
            auth3dsDetails.setAuth3dsResult(getMappedAuth3dsResult(type));
            card3dsResponseAuthService.process3DSecureAuthorisationWithoutLocking(charge.getExternalId(), auth3dsDetails);
        } catch (OperationAlreadyInProgressRuntimeException e) {
            // CardExecutorService is asynchronous and sends back 'OperationAlreadyInProgressRuntimeException' 
            // exception while the charge is being authorised. Catch this exception to send a response with 
            // http status 200 instead of depending on the status returned by Exception 
        }
    }

    private boolean isASourceNotification(StripeNotification notification) {
        return sourceTypes.contains(StripeNotificationType.byType(notification.getType()));
    }
    
    private boolean isAPaymentIntentNotification(StripeNotification notification) {
        return paymentIntentTypes.contains(StripeNotificationType.byType(notification.getType()));
    }

    private String getMappedAuth3dsResult(StripeNotificationType type) {
        switch (type) {
            case SOURCE_CHARGEABLE:
            case PAYMENT_INTENT_AMOUNT_CAPTURABLE_UPDATED:
                return Auth3dsDetails.Auth3dsResult.AUTHORISED.toString();
            case SOURCE_CANCELED:
                return Auth3dsDetails.Auth3dsResult.CANCELED.toString();
            case SOURCE_FAILED:
            case PAYMENT_INTENT_PAYMENT_FAILED:
                return Auth3dsDetails.Auth3dsResult.DECLINED.toString();
            default:
                return Auth3dsDetails.Auth3dsResult.ERROR.toString();
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
