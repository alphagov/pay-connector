package uk.gov.pay.connector.gateway.adyen.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.util.IpDomainMatcher;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenRecurringTokenNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenRecurringTokenNotificationService.class);

    private final AdyenGatewayConfig adyenGatewayConfig;
    private final IpDomainMatcher ipDomainMatcher;
    private final ObjectMapper objectMapper;
    private static final String NOTIFICATION_SOURCE = "notification_source";

    @Inject
    public AdyenRecurringTokenNotificationService(AdyenGatewayConfig adyenGatewayConfig,
                                                  IpDomainMatcher ipDomainMatcher,
                                                  ObjectMapper objectMapper) {
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.ipDomainMatcher = ipDomainMatcher;
        this.objectMapper = objectMapper;
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

        if (isBlank(hmacSignature)) {
            LOGGER.atInfo()
                    .setMessage("Adyen token notification missing hmacSignature header")
                    .addKeyValue(PROVIDER, PaymentGatewayName.ADYEN.getName())
                    .addKeyValue(NOTIFICATION_SOURCE, forwardedIpAddresses)
                    .log();
            return false;
        }

        validatePayload(payload, forwardedIpAddresses);

        LOGGER.atInfo()
                .setMessage("Processed Adyen token notification")
                .addKeyValue(PROVIDER, PaymentGatewayName.ADYEN.getName())
                .addKeyValue(NOTIFICATION_SOURCE, forwardedIpAddresses)
                .log();
        return true;
    }

    private void validatePayload(String payload, String forwardedIpAddresses) {
        try {
            objectMapper.readTree(payload);
        } catch (IOException exception) {
            LOGGER.atInfo()
                    .setMessage("Error deserialising Adyen token notification payload")
                    .addKeyValue(PROVIDER, PaymentGatewayName.ADYEN.getName())
                    .addKeyValue(NOTIFICATION_SOURCE, forwardedIpAddresses)
                    .setCause(exception)
                    .log();
            throw new WebApplicationException("Error deserialising Adyen token webhook Json", exception);
        }
    }
}
