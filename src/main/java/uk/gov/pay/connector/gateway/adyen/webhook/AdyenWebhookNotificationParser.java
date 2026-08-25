package uk.gov.pay.connector.gateway.adyen.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenEnvironment;
import uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookEvent;
import uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookNotification;

import java.util.Optional;

import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenEnvironment.LIVE;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenEnvironment.TEST;

public class AdyenWebhookNotificationParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenWebhookNotificationParser.class);

    public AdyenWebhookNotification parse(JsonNode root) {
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
        Optional<AdyenWebhookEvent> mayBeAdyenWebhookEvent = AdyenWebhookEvent.fromEventCodeOrType(eventCodeOrType);

        if (mayBeAdyenWebhookEvent.isPresent()) {
            return mayBeAdyenWebhookEvent.get();
        } else {
            LOGGER.atWarn()
                    .setMessage("Unrecognised Adyen eventCode or type")
                    .addKeyValue("eventCodeOrType", eventCodeOrType)
                    .log();
            throw new UnparseableAdyenWebhookException("Unrecognised eventCode or type: " + eventCodeOrType);
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
        String environment = root.path("environment").asText();
        return switch (environment) {
            case "live" -> LIVE;
            case "test" -> TEST;
            default ->
                    throw new UnparseableAdyenWebhookException("Unrecognised 'environment' field value: " + environment);
        };
    }
}
