package uk.gov.pay.connector.gateway.adyen.webhook;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.util.IpDomainMatcher;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenNotificationValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenNotificationValidator.class);
    private static final String NOTIFICATION_SOURCE = "notification_source";

    private final IpDomainMatcher ipDomainMatcher;
    private final String notificationDomain;

    @Inject
    public AdyenNotificationValidator(AdyenGatewayConfig gatewayConfig, IpDomainMatcher ipDomainMatcher) {
        this.notificationDomain = gatewayConfig.getNotificationDomain();
        this.ipDomainMatcher = ipDomainMatcher;
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
}
