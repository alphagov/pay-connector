package uk.gov.pay.connector.gateway.adyen.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenEnvironment;
import uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookEvent;
import uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookNotification;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenEnvironment.LIVE;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenEnvironment.TEST;

public class AdyenWebhookNotificationParser {
    private final ObjectMapper objectMapper;
    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenWebhookNotificationParser.class);

    @Inject
    public AdyenWebhookNotificationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AdyenWebhookNotification parse(String rawPayload) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException _) {
            throw new UnparseableAdyenWebhookException("Failed to parse Adyen notification from raw payload");
        }
        if (root.has("notificationItems")) {
            return processNotificationItem(root);
        }
        if (root.has("type")) {
            return processNotificationByType(root);
        }
        throw new UnparseableAdyenWebhookException("No recognised field in the payload to process Adyen notification");
    }

    private AdyenWebhookNotification processNotificationItem(JsonNode root) {
        String eventCode = root
                .path("notificationItems").path(0)
                .path("NotificationRequestItem")
                .path("eventCode").asText();

        AdyenWebhookEvent adyenWebhookEvent = deriveWebhookEvent(eventCode);
        AdyenEnvironment environment = deriveAdyenEnvironmentFromNotificationItem(root);
        return new AdyenWebhookNotification(adyenWebhookEvent, environment, true);
    }

    private AdyenWebhookNotification processNotificationByType(JsonNode root) {
        String typeField = root.get("type").asText();
        AdyenWebhookEvent adyenWebhookEvent = deriveWebhookEvent(typeField);
        AdyenEnvironment environment = deriveAdyenEnvironment(root);
        return new AdyenWebhookNotification(adyenWebhookEvent, environment, false);
    }

    private AdyenWebhookEvent deriveWebhookEvent(String eventCodeOrType) {
        Optional<AdyenWebhookEvent> adyenWebhookEvent = AdyenWebhookEvent.fromEventCodeOrType(eventCodeOrType);

        if (adyenWebhookEvent.isPresent()) {
            return adyenWebhookEvent.get();
        } else {
            LOGGER.atWarn()
                    .setMessage("Unrecognised Adyen eventCode or type")
                    .addKeyValue("adyen_event_code_or_type", eventCodeOrType)
                    .log();
            throw new UnrecognisedWebhookEventException(eventCodeOrType);
        }
    }

    private AdyenEnvironment deriveAdyenEnvironmentFromNotificationItem(JsonNode root) {
        String live = root.path("live").asText();
        return switch (live) {
            case "true" -> LIVE;
            case "false" -> TEST;
            default -> throw new UnparseableAdyenWebhookException("Unrecognised 'live' field value: " + live);
        };
    }

    private AdyenEnvironment deriveAdyenEnvironment(JsonNode root) {
        var environment = root.path("environment").asText();
        return switch (environment) {
            case "live" -> LIVE;
            case "test" -> TEST;
            default ->
                    throw new UnparseableAdyenWebhookException("Unrecognised 'environment' field value: " + environment);
        };
    }
}
