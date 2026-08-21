package uk.gov.pay.connector.gateway.adyen.webhook;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.notification.WebhookHandler;
import com.google.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.adyen.response.AdyenTokenNotification;
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.queue.tasks.TaskType;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getHmacKey;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getTokenHmacKey;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenNotificationService.class);

    private final AdyenGatewayConfig adyenGatewayConfig;
    private final TaskQueueService taskQueueService;
    private final AdyenNotificationValidator adyenNotificationValidator;
    private final JsonObjectMapper jsonObjectMapper;

    @Inject
    public AdyenNotificationService(AdyenGatewayConfig adyenGatewayConfig, TaskQueueService taskQueueService, AdyenNotificationValidator adyenNotificationValidator, JsonObjectMapper jsonObjectMapper) {
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.taskQueueService = taskQueueService;
        this.adyenNotificationValidator = adyenNotificationValidator;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public boolean handleNotificationFor(String payload, String hmacSignature, String forwardedIpAddresses) {
        if (!adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses)) {
            return false;
        }
        try {
            var notificationProcessed = hmacSignature == null ? handlePaymentNotifications(payload) : handleTokenNotifications(payload,
                    hmacSignature);

            if (notificationProcessed) {
                LOGGER.info("Processed Adyen notification", kv(PROVIDER, ADYEN.getName()),
                        kv("notification_source", forwardedIpAddresses));
            }

            return notificationProcessed;
        } catch (AdyenNotificationException e) {
            logValidationFailure(e);
            return false;
        }
    }

    private boolean handlePaymentNotifications(String payload) {

        NotificationRequest notificationRequest = deserialisePayloadToNotificationRequest(payload);
        List<NotificationRequestItem> items = extractNotificationItems(notificationRequest);

        boolean live = "true".equalsIgnoreCase(notificationRequest.getLive());

        String hmacKey = getHmacKey(adyenGatewayConfig, live);

        for (NotificationRequestItem item : items) {
            if (!adyenNotificationValidator.isValidHmac(item, hmacKey)) {
                return false;
            }

            if (!AdyenPaymentEvent.contains(item.getEventCode())) {
                return false;
            }
            addNotificationToTaskQueue(payload, TaskType.HANDLE_ADYEN_PAYMENTS_WEBHOOK_NOTIFICATION);
        }
        return true;
    }

    private boolean handleTokenNotifications(String payload, String hmacSignature) {

        if (hmacSignature.isBlank()) {
            logInvalidHmacSignature();
            return false;
        }

        AdyenTokenNotification adyenTokenResponse = deserialiseTokenPayload(payload, AdyenTokenNotification.class);
        boolean live = adyenTokenResponse
                .environment()
                .equalsIgnoreCase("live");

        if (!AdyenTokenEvent.contains(adyenTokenResponse.type())) {
            return false;
        }

        String hmacKey = getTokenHmacKey(adyenGatewayConfig, live);
        if (!adyenNotificationValidator.isValidHmac(hmacSignature, hmacKey, payload)) {
            logInvalidHmacSignature();
            return false;
        }

        addNotificationToTaskQueue(payload, TaskType.HANDLE_ADYEN_TOKEN_WEBHOOK_NOTIFICATION);

        return true;
    }

    private static void logInvalidHmacSignature() {
        LOGGER
                .atInfo()
                .setMessage("Hmac signature is invalid or missing, rejecting Adyen token notification")
                .addKeyValue(PROVIDER, ADYEN.getName())
                .log();
    }

    public NotificationRequest deserialisePayloadToNotificationRequest(String rawAdyenJson) {
        try {
            WebhookHandler webhookHandler = new WebhookHandler();
            return webhookHandler.handleNotificationJson(rawAdyenJson);
        } catch (Exception e) {
            LOGGER.info("Error deserialising Adyen notification payload", e);
            throw new WebApplicationException("Error deserialising webhook Json", e);
        }
    }

    public List<NotificationRequestItem> extractNotificationItems(NotificationRequest notificationRequest) {
        if (notificationRequest == null || (notificationRequest.getNotificationItems() == null || notificationRequest
                .getNotificationItems()
                .isEmpty())) {
            LOGGER.info("Adyen notification request is empty or missing items");
            throw new AdyenNotificationException("Notification request is empty");
        }
        return notificationRequest.getNotificationItems();
    }


    public <T> T deserialiseTokenPayload(String payload, Class<T> targetClass) throws AdyenNotificationException {
        try {
            return jsonObjectMapper.getObject(payload, targetClass);
        } catch (Exception e) {
            LOGGER.info("Error deserialising token notification payload", e);
            throw new WebApplicationException("Error deserialising token webhook Json", e);
        }
    }

    private void logValidationFailure(AdyenNotificationException e) {
        LOGGER.error("Failed to validate Adyen notification payload", e);
    }

    private void addNotificationToTaskQueue(String payload, TaskType task) {
        try {
            taskQueueService.add(new Task(payload, task));
        } catch (Exception e) {
            LOGGER.error("Error sending Adyen webhook notification to task SQS queue", e);

            throw new WebApplicationException("Error sending message to task SQS queue", e);
        }
    }
}
