package uk.gov.pay.connector.gateway.adyen.webhook;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.util.IpDomainMatcher;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenNotificationService.class);

    private final AdyenGatewayConfig adyenGatewayConfig;
    private final IpDomainMatcher ipDomainMatcher;
    
    @Inject
    public AdyenNotificationService(AdyenGatewayConfig adyenGatewayConfig, IpDomainMatcher ipDomainMatcher) {
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.ipDomainMatcher = ipDomainMatcher;
    }

    public boolean handleNotificationFor(String payload, String forwardedIpAddresses) {
        if (isBlank(forwardedIpAddresses)) {
            LOGGER.info("Adyen notification missing X-Forwarded-For header",
                    kv(PROVIDER, PaymentGatewayName.ADYEN.getName()));
            return false;
        }

        String notificationDomain = adyenGatewayConfig.getNotificationDomain();

        if (!ipDomainMatcher.ipMatchesDomain(forwardedIpAddresses, notificationDomain)) {
            LOGGER.info("Adyen notification from ip '{}' not matching configured domain '{}'",
                    forwardedIpAddresses,
                    notificationDomain,
                    kv(PROVIDER, PaymentGatewayName.ADYEN.getName()),
                    kv("notification_source", forwardedIpAddresses));
            return false;
        }

        LOGGER.info("Processed adyen notification",
                kv(PROVIDER, PaymentGatewayName.ADYEN.getName()),
                kv("notification_source", forwardedIpAddresses),
                kv("payload_length", payload == null ? 0 : payload.length()));

        return true;
    }
}
