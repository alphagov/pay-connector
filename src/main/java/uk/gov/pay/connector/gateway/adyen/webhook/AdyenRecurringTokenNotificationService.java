package uk.gov.pay.connector.gateway.adyen.webhook;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.util.IpDomainMatcher;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenRecurringTokenNotificationService {

    private final AdyenGatewayConfig adyenGatewayConfig;
    private final AdyenNotificationValidator adyenNotificationValidator;
    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenRecurringTokenNotificationService.class);


    @Inject
    public AdyenRecurringTokenNotificationService(AdyenGatewayConfig adyenGatewayConfig,
                                                  IpDomainMatcher ipDomainMatcher) {
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.adyenNotificationValidator = new AdyenNotificationValidator(ipDomainMatcher);
    }

    public boolean handleNotificationFor(String payload, String hmacSignature, String forwardedIpAddresses) {
        String notificationDomain = adyenGatewayConfig.getNotificationDomain();

        if (!adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses, notificationDomain)) {
            return false;
        }

        LOGGER.atInfo()
                .setMessage("Processed Adyen notification")
                .addKeyValue(PROVIDER, ADYEN.getName())
                .addKeyValue("notification_source", forwardedIpAddresses)
                .log();
        return true;
    }
}
