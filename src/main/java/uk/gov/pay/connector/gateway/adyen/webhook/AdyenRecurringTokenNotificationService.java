package uk.gov.pay.connector.gateway.adyen.webhook;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getTokenHmacKey;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenRecurringTokenNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenRecurringTokenNotificationService.class);
    private final AdyenNotificationValidator adyenNotificationValidator;
    private static final String NOTIFICATION_SOURCE = "notification_source";
    private final AdyenGatewayConfig adyenGatewayConfig;

    @Inject
    public AdyenRecurringTokenNotificationService(AdyenGatewayConfig adyenGatewayConfig, AdyenNotificationValidator adyenNotificationValidator) {
        this.adyenNotificationValidator = adyenNotificationValidator;
        this.adyenGatewayConfig = adyenGatewayConfig;
    }

    public boolean handleNotificationFor(String payload, String hmacSignature, String forwardedIpAddresses) {
        try {
            if (!adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses)) {
                return false;
            }

            if (hmacSignature == null || hmacSignature.isBlank()) {
                LOGGER.atInfo()
                        .setMessage("Hmac signature is missing, rejecting Adyen token notification")
                        .addKeyValue(PROVIDER, ADYEN.getName())
                        .log();
                return false;
            }

            // Todo: need to work out how to set this. Hardcoding for now 
            boolean live = false;
            String hmacKey = getTokenHmacKey(adyenGatewayConfig, live);
            if (!adyenNotificationValidator.isValidHmac(hmacSignature, hmacKey, payload)) {
                LOGGER.atInfo()
                        .setMessage("Hmac signature is invalid, rejecting Adyen token notification")
                        .addKeyValue(PROVIDER, ADYEN.getName())
                        .log();
                return false;
            }

            LOGGER.atInfo()
                    .setMessage("Processed Adyen token notification")
                    .addKeyValue(PROVIDER, ADYEN.getName())
                    .addKeyValue(NOTIFICATION_SOURCE, forwardedIpAddresses)
                    .log();
            return true;
        } catch (AdyenNotificationException e) {
            LOGGER.error("Failed to validate Adyen token notification payload", e);
            return false;
        }
    }

}
