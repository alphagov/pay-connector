package uk.gov.pay.connector.gateway.adyen.webhook;

import com.adyen.util.HMACValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil;
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.queue.tasks.TaskType;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.pay.connector.util.IpDomainMatcher;

import java.security.SignatureException;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenTokenNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenTokenNotificationService.class);

    private final AdyenGatewayConfig adyenGatewayConfig;
    private final IpDomainMatcher ipDomainMatcher;
    private final HMACValidator hmacValidator;
    private final TaskQueueService taskQueueService;
    private final ObjectMapper objectMapper;

    @Inject
    public AdyenTokenNotificationService(AdyenGatewayConfig adyenGatewayConfig,
                                         IpDomainMatcher ipDomainMatcher,
                                         TaskQueueService taskQueueService,
                                         ObjectMapper objectMapper) {
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.ipDomainMatcher = ipDomainMatcher;
        this.taskQueueService = taskQueueService;
        this.objectMapper = objectMapper;
        this.hmacValidator = new HMACValidator();
    }

    public boolean handleNotificationFor(String payload, String hmacSignature, String forwardedIpAddresses) {
        if (isBlank(forwardedIpAddresses)) {
            LOGGER.info("Adyen token notification missing X-Forwarded-For header",
                    kv(PROVIDER, PaymentGatewayName.ADYEN.getName()));
            return false;
        }

        String notificationDomain = adyenGatewayConfig.getNotificationDomain();

        if (!ipDomainMatcher.ipMatchesDomain(forwardedIpAddresses, notificationDomain)) {
            LOGGER.info("Adyen token notification from ip '{}' not matching configured domain '{}'",
                    forwardedIpAddresses,
                    notificationDomain,
                    kv(PROVIDER, PaymentGatewayName.ADYEN.getName()),
                    kv("notification_source", forwardedIpAddresses));
            return false;
        }

        if (isBlank(hmacSignature)) {
            LOGGER.info("Adyen token notification missing hmacSignature header",
                    kv(PROVIDER, PaymentGatewayName.ADYEN.getName()));
            return false;
        }

        try {
            boolean live = isLiveEnvironment(payload);
            String hmacKey = AdyenConfigUtil.getTokenHmacKey(adyenGatewayConfig, live);

            if (!isValidHmac(hmacSignature, hmacKey, payload)) {
                return false;
            }

            String type = extractNotificationType(payload);
            if (AdyenTokenEvent.contains(type)) {
                addNotificationToTaskQueue(payload, type);
            } else {
                LOGGER.info("Ignored Adyen token notification", kv("type", type));
            }
        } catch (AdyenNotificationException e) {
            LOGGER.error("Failed to validate Adyen token notification payload", e);
            return false;
        }

        LOGGER.info("Processed Adyen token notification",
                kv(PROVIDER, PaymentGatewayName.ADYEN.getName()),
                kv("notification_source", forwardedIpAddresses));

        return true;
    }

    private void addNotificationToTaskQueue(String payload, String type) {
        try {
            taskQueueService.add(new Task(payload, TaskType.HANDLE_ADYEN_TOKEN_WEBHOOK_NOTIFICATION));
        } catch (Exception e) {
            LOGGER.error("Error sending Adyen token webhook notification to task SQS queue",
                    kv("type", type),
                    e);
            throw new WebApplicationException(
                    "Error sending message to task SQS queue",
                    e);
        }
    }

    private boolean isLiveEnvironment(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode environment = root.get("environment");

            if (environment == null || environment.isNull() || isBlank(environment.asText())) {
                LOGGER.info("Adyen token notification missing environment field");
                throw new AdyenNotificationException("Missing environment field");
            }

            return "live".equalsIgnoreCase(environment.asText());
        } catch (JsonProcessingException e) {
            LOGGER.info("Error deserialising Adyen token notification payload", e);
            throw new WebApplicationException("Error deserialising webhook Json", e);
        }
    }

    private String extractNotificationType(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode type = root.get("type");

            if (type == null || type.isNull() || isBlank(type.asText())) {
                LOGGER.info("Adyen token notification missing type field");
                throw new AdyenNotificationException("Missing type field");
            }

            return type.asText();
        } catch (JsonProcessingException e) {
            LOGGER.info("Error deserialising Adyen token notification payload", e);
            throw new WebApplicationException("Error deserialising webhook Json", e);
        }
    }

    private boolean isValidHmac(String hmacSignature, String hmacKey, String payload) {
        try {
            boolean validSignature = hmacValidator.validateHMAC(hmacSignature, hmacKey, payload);

            if (!validSignature) {
                LOGGER.error("Invalid HMAC signature in the header for Adyen token notification");
            }
            return validSignature;
        } catch (IllegalArgumentException | SignatureException e) {
            LOGGER.info("Failed to validate HMAC signature for Adyen token notification");
            throw new AdyenNotificationException("Failed to validate HMAC signature", e);
        }
    }
}
