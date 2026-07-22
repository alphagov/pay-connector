package uk.gov.pay.connector.gateway.adyen.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.util.IpDomainMatcher;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenNotificationValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenNotificationValidator.class);
    private static final String ADYEN_PROVIDER = PaymentGatewayName.ADYEN.getName();
    private static final String NOTIFICATION_SOURCE = "notification_source";

    private final IpDomainMatcher ipDomainMatcher;

    public AdyenNotificationValidator(IpDomainMatcher ipDomainMatcher) {
        this.ipDomainMatcher = ipDomainMatcher;
    }

    public boolean isValidIpAddress(String forwardedIpAddresses, String notificationDomain) {
        if (isBlank(forwardedIpAddresses)) {
            LOGGER.atInfo()
                    .setMessage("Adyen notification missing X-Forwarded-For header")
                    .addKeyValue(PROVIDER, ADYEN_PROVIDER)
                    .log();
            return false;
        }

        if (!ipDomainMatcher.ipMatchesDomain(forwardedIpAddresses, notificationDomain)) {
            LOGGER.atInfo()
                    .addKeyValue(PROVIDER, ADYEN_PROVIDER)
                    .addKeyValue(NOTIFICATION_SOURCE, forwardedIpAddresses)
                    .log("Adyen notification from ip '{}' not matching configured domain '{}'",
                            forwardedIpAddresses, notificationDomain);
            return false;
        }

        return true;
    }

    public void logSuccessfulValidation(String forwardedIpAddresses) {
        LOGGER.atInfo()
                .setMessage("Processed Adyen notification")
                .addKeyValue(PROVIDER, ADYEN_PROVIDER)
                .addKeyValue(NOTIFICATION_SOURCE, forwardedIpAddresses)
                .log();
    }
}
