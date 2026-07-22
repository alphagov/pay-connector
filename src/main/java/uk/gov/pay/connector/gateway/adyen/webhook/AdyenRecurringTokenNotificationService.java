package uk.gov.pay.connector.gateway.adyen.webhook;

import jakarta.inject.Inject;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.util.IpDomainMatcher;

public class AdyenRecurringTokenNotificationService {

    private final AdyenGatewayConfig adyenGatewayConfig;
    private final AdyenNotificationValidator adyenNotificationValidator;

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

        adyenNotificationValidator.logSuccessfulValidation(forwardedIpAddresses);
        return true;
    }
}
