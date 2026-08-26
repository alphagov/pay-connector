package uk.gov.pay.connector.gateway.adyen.webhook;

import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookNotification;
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;
import uk.gov.pay.connector.util.IpDomainMatcher;

import java.security.SignatureException;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getHmacKeyForWebhookType;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenNotificationValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenNotificationValidator.class);
    private static final String NOTIFICATION_SOURCE = "notification_source";

    private final IpDomainMatcher ipDomainMatcher;
    private final String notificationDomain;
    private final HMACValidator hmacValidator;
    private final AdyenWebhookDeserialiser adyenWebhookDeserialiser;
    private final AdyenGatewayConfig adyenGatewayConfig;

    @Inject
    public AdyenNotificationValidator(AdyenGatewayConfig gatewayConfig, IpDomainMatcher ipDomainMatcher,
                                      HMACValidator hmacValidator,
                                      AdyenWebhookDeserialiser adyenWebhookDeserialiser) {
        adyenGatewayConfig = gatewayConfig;
        this.notificationDomain = gatewayConfig.getNotificationDomain();
        this.ipDomainMatcher = ipDomainMatcher;
        this.hmacValidator = hmacValidator;
        this.adyenWebhookDeserialiser = adyenWebhookDeserialiser;
    }

    public boolean isValidIpAddress(String forwardedIpAddresses) {
        if (isBlank(forwardedIpAddresses)) {
            LOGGER.atInfo()
                    .setMessage("Adyen notification missing X-Forwarded-For header")
                    .addKeyValue(PROVIDER, ADYEN.getName())
                    .log();
            return false;
        }

        if (!ipDomainMatcher.ipMatchesDomain(forwardedIpAddresses, notificationDomain)) {
            LOGGER.atInfo()
                    .addKeyValue(PROVIDER, ADYEN.getName())
                    .addKeyValue(NOTIFICATION_SOURCE, forwardedIpAddresses)
                    .log("Adyen notification from ip '{}' not matching configured domain '{}'",
                            forwardedIpAddresses, notificationDomain);
            return false;
        }

        return true;
    }

    public boolean validateHmacSignature(AdyenWebhookNotification adyenWebhookNotification,
                                         String payload,
                                         String hmacSignature) {
        String hmacKey = getHmacKeyForWebhookType(adyenGatewayConfig,
                adyenWebhookNotification.event().getWebhookType(),
                adyenWebhookNotification.isLive());

        if (adyenWebhookNotification.usesAdyenNotificationItem()) {
            NotificationRequestItem notificationRequestItem = adyenWebhookDeserialiser.deserialiseAndGetNotificationItem(payload);
            return isValidHmac(notificationRequestItem, hmacKey);
        } else {
            if (isBlank(hmacSignature)) {
                LOGGER
                        .atInfo()
                        .setMessage("Hmac signature is missing, rejecting Adyen token notification")
                        .addKeyValue(PROVIDER, ADYEN.getName())
                        .log();
                throw new AdyenNotificationException("Missing hmacSignature for the notification");
            }
            return isValidHmac(hmacSignature, hmacKey, payload);
        }
    }

    public boolean isValidHmac(NotificationRequestItem item, String hmacKey) throws AdyenNotificationException {
        try {
            boolean validSignature = hmacValidator.validateHMAC(item, hmacKey);

            if (!validSignature) {
                LOGGER.atError()
                        .setMessage("Invalid HMAC signature in the payload for Adyen notification")
                        .addKeyValue("pspReference", item.getPspReference())
                        .addKeyValue("eventCode", item.getEventCode())
                        .log();
            }
            return validSignature;
        } catch (IllegalArgumentException | SignatureException e) {
            LOGGER.atInfo()
                    .setMessage("Failed to validate HMAC signature")
                    .addKeyValue("pspReference", item.getPspReference())
                    .addKeyValue("eventCode", item.getEventCode())
                    .log();
            throw new AdyenNotificationException("Failed to validate HMAC signature", e);
        }
    }

    public boolean isValidHmac(String hmacSignature, String hmacKey, String payload) throws AdyenNotificationException {
        try {
            return hmacValidator.validateHMAC(hmacSignature, hmacKey, payload);
        } catch (IllegalArgumentException | SignatureException e) {
            LOGGER.atInfo()
                    .setMessage("Failed to validate HMAC signature for token notification")
                    .log();
            throw new AdyenNotificationException("Failed to validate HMAC signature for token notification", e);
        }
    }
}
