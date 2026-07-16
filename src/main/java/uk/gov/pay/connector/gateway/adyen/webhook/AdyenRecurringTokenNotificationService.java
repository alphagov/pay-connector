package uk.gov.pay.connector.gateway.adyen.webhook;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.util.IpDomainMatcher;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenRecurringTokenNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenRecurringTokenNotificationService.class);

    private final AdyenGatewayConfig adyenGatewayConfig;
    private final IpDomainMatcher ipDomainMatcher;
    private static final String NOTIFICATION_SOURCE = "notification_source";

    @Inject
    public AdyenRecurringTokenNotificationService(AdyenGatewayConfig adyenGatewayConfig,
                                                  IpDomainMatcher ipDomainMatcher) {
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.ipDomainMatcher = ipDomainMatcher;
    }

    public boolean handleNotificationFor(String payload, String hmacSignature, String forwardedIpAddresses) {
        if (isBlank(forwardedIpAddresses)) {
            LOGGER.atInfo()
                    .setMessage("Adyen token notification missing X-Forwarded-For header")
                    .addKeyValue("provider", PaymentGatewayName.ADYEN.getName())
                    .log();
            return false;
        }

        String notificationDomain = adyenGatewayConfig.getNotificationDomain();
        if (!ipDomainMatcher.ipMatchesDomain(forwardedIpAddresses, notificationDomain)) {
            LOGGER.atInfo()
                    .addKeyValue(PROVIDER, PaymentGatewayName.ADYEN.getName())
                    .addKeyValue(NOTIFICATION_SOURCE, forwardedIpAddresses)
                    .log("Adyen token notification from ip '{}' not matching configured domain '{}'", forwardedIpAddresses, notificationDomain);
            return false;
        }

        LOGGER.atInfo()
                .setMessage("Processed Adyen token notification")
                .addKeyValue(PROVIDER, PaymentGatewayName.ADYEN.getName())
                .addKeyValue(NOTIFICATION_SOURCE, forwardedIpAddresses)
                .log();
        return true;
    }
}
