package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
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
import uk.gov.pay.connector.gateway.stripe.json.StripeSourcesResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.paymentprocessor.service.Card3dsResponseAuthService;

import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CANCELED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_CHARGEABLE;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.SOURCE_FAILED;
import static uk.gov.pay.connector.paymentprocessor.model.OperationType.AUTHORISATION_3DS;

public class StripeNotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<StripeNotificationType> sourceTypes = ImmutableList.of(SOURCE_CANCELED, SOURCE_CHARGEABLE, SOURCE_FAILED);
    private final List<ChargeStatus> threeDSAuthorisableStates = ImmutableList.of(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_3DS_READY);

    private final ChargeService chargeService;
    private final Card3dsResponseAuthService card3dsResponseAuthService;
    private final ObjectMapper objectMapper;
    private final StripeGatewayConfig stripeGatewayConfig;

    private static final String PAYMENT_GATEWAY_NAME = PaymentGatewayName.STRIPE.getName();
    private static final long DEFAULT_TOLERANCE = 300L;

    @Inject
    public StripeNotificationService(Card3dsResponseAuthService card3dsResponseAuthService,
                                     ChargeService chargeService,
                                     StripeGatewayConfig stripeGatewayConfig) {
        this.card3dsResponseAuthService = card3dsResponseAuthService;
        this.chargeService = chargeService;
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
                authorise3DSSource(charge, notification.getType());
            }

        } catch (StripeParseException e) {
            logger.error("{} notification parsing for source object failed: {}", PAYMENT_GATEWAY_NAME, e);
        }
    }

    private void authorise3DSSource(ChargeEntity charge, String notificationEventType) {
        try {
            final StripeNotificationType type = StripeNotificationType.byType(notificationEventType);

            if (AUTHORISATION_3DS_REQUIRED.equals(ChargeStatus.fromString(charge.getStatus()))) {
                logger.info("Updating charge for notification - " +
                                "charge_external_id={}, status={}, status_to={}, transaction_id={}, " +
                                "account_id={}, provider={}, provider_type={}",
                        charge.getExternalId(), charge.getStatus(), charge.getStatus(), charge.getGatewayTransactionId(),
                        charge.getGatewayAccount().getId(), charge.getGatewayAccount().getGatewayName(), charge.getGatewayAccount().getType());
                chargeService.lockChargeForProcessing(charge.getExternalId(), AUTHORISATION_3DS);
            }

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

    private String getMappedAuth3dsResult(StripeNotificationType type) {
        switch (type) {
            case SOURCE_CHARGEABLE:
                return Auth3dsDetails.Auth3dsResult.AUTHORISED.toString();
            case SOURCE_CANCELED:
                return Auth3dsDetails.Auth3dsResult.CANCELED.toString();
            case SOURCE_FAILED:
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
        if (isValidNotificationSignature(payload, signatureHeader, stripeGatewayConfig.getWebhookSigningSecrets().getTest()) || 
                isValidNotificationSignature(payload, signatureHeader, stripeGatewayConfig.getWebhookSigningSecrets().getLive())) {
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
