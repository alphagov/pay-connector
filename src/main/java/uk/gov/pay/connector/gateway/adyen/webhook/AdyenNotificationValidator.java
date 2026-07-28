package uk.gov.pay.connector.gateway.adyen.webhook;

import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;
import uk.gov.pay.connector.util.IpDomainMatcher;

import java.security.SignatureException;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenNotificationValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenNotificationValidator.class);
    private static final String NOTIFICATION_SOURCE = "notification_source";

    private final IpDomainMatcher ipDomainMatcher;
    private final String notificationDomain;
    private final HMACValidator hmacValidator;

    @Inject
    public AdyenNotificationValidator(AdyenGatewayConfig gatewayConfig, IpDomainMatcher ipDomainMatcher, HMACValidator hmacValidator) {
        this.notificationDomain = gatewayConfig.getNotificationDomain();
        this.ipDomainMatcher = ipDomainMatcher;
        this.hmacValidator = hmacValidator;
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

    public boolean isValidHmac(NotificationRequestItem item, String hmacKey) {
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
}
