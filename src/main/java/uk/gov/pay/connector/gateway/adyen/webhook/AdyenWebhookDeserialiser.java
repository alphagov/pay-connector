package uk.gov.pay.connector.gateway.adyen.webhook;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.notification.WebhookHandler;
import com.google.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;
import uk.gov.pay.connector.util.JsonObjectMapper;

public class AdyenWebhookDeserialiser {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenWebhookDeserialiser.class);

    private final JsonObjectMapper jsonObjectMapper;

    @Inject
    public AdyenWebhookDeserialiser(JsonObjectMapper jsonObjectMapper) {
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public NotificationRequestItem deserialiseAndGetNotificationItem(String payload) {
        try {
            WebhookHandler webhookHandler = new WebhookHandler();
            NotificationRequest notificationRequest = webhookHandler.handleNotificationJson(payload);
            return extractNotificationItem(notificationRequest);
        } catch (Exception e) {
            LOGGER.info("Error deserialising Adyen notification payload", e);
            throw new WebApplicationException("Error deserialising notification payload", e);
        }
    }

    public <T> T deserialisePayload(String payload, Class<T> targetClass) throws AdyenNotificationException {
        try {
            return jsonObjectMapper.getObject(payload, targetClass);
        } catch (Exception e) {
            LOGGER.info("Error deserialising notification payload to class {}", targetClass.getSimpleName(), e);
            throw new WebApplicationException("Error deserialising notification payload", e);
        }
    }

    private NotificationRequestItem extractNotificationItem(NotificationRequest notificationRequest) {
        if (notificationRequest == null || (notificationRequest.getNotificationItems() == null || notificationRequest
                .getNotificationItems()
                .isEmpty())) {
            LOGGER.info("Adyen notification request is empty or missing items");
            throw new AdyenNotificationException("Notification request is empty");
        }

        if (notificationRequest.getNotificationItems().size() > 1) {
            LOGGER.info("Excepted Adyen notification to have one NotificationItem but found {} items", notificationRequest.getNotificationItems().size());
            throw new AdyenNotificationException("Excepted Adyen notification to have one NotificationItem but found more than one");
        }

        return notificationRequest.getNotificationItems().getFirst();
    }

}
