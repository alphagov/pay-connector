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
    public AdyenNotificationService(AdyenGatewayConfig adyenGatewayConfig, TaskQueueService taskQueueService, 
                                    AdyenNotificationValidator adyenNotificationValidator, JsonObjectMapper jsonObjectMapper) {
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.taskQueueService = taskQueueService;
        this.adyenNotificationValidator = adyenNotificationValidator;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public boolean handleNotificationFor(String payload, String forwardedIpAddresses) {

        if (!adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses)) {
            return false;
        }

        try {
            NotificationRequest notificationRequest = deserialisePayloadToNotificationRequest(payload);
            List<NotificationRequestItem> items = extractNotificationItems(notificationRequest);

            boolean live = "true".equalsIgnoreCase(notificationRequest.getLive());

            for (NotificationRequestItem item : items) {
                if (!adyenNotificationValidator.isValidHmac(item, getHmacKey(adyenGatewayConfig, live))) {
                    return false;
                }

                if (AdyenPaymentEvent.contains(item.getEventCode())) {
                    addNotificationToTaskQueue(payload, item);
                    continue;
                }

                LOGGER.info("Ignored Adyen notification",
                        kv("originalReference", item.getOriginalReference()),
                        kv("eventCode", item.getEventCode()));
            }
        } catch (AdyenNotificationException e) {
            logValidationFailure("payment", e);
            return false;
        }

        LOGGER.info("Processed Adyen notification",
                kv(PROVIDER, ADYEN.getName()),
                kv("notification_source", forwardedIpAddresses));

        return true;
    }

    private void addNotificationToTaskQueue(String payload, NotificationRequestItem item) {
        try {
            taskQueueService.add(new Task(payload, TaskType.HANDLE_ADYEN_PAYMENTS_WEBHOOK_NOTIFICATION));
        } catch (Exception e) {
            LOGGER.error("Error sending Adyen webhook notification to task SQS queue",
                    kv("pspReference", item.getPspReference()),
                    kv("eventCode", item.getEventCode()),
                    e);
            throw new WebApplicationException(
                    "Error sending message to task SQS queue",
                    e);
        }
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
        if (notificationRequest == null ||
                (notificationRequest.getNotificationItems() == null || notificationRequest.getNotificationItems().isEmpty())) {
            LOGGER.info("Adyen notification request is empty or missing items");
            throw new AdyenNotificationException("Notification request is empty");
        }
        return notificationRequest.getNotificationItems();
    }


    public boolean handleNotificationFor(
            String payload,
            String hmacSignature,
            String forwardedIpAddresses
    ) {
        try {
            if (!adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses)) {
                return false;
            }
            
            if (hmacSignature == null || hmacSignature.isBlank()) {
                LOGGER.atInfo().setMessage("Hmac signature is missing, rejecting Adyen token notification")
                        .addKeyValue(PROVIDER, ADYEN.getName()).log();
                return false;
            }

            AdyenTokenNotification adyenTokenResponse = deserialiseTokenPayload(payload, AdyenTokenNotification.class);
            boolean live = adyenTokenResponse.environment().equalsIgnoreCase("live");
            
            String hmacKey = getTokenHmacKey(adyenGatewayConfig, live);

            if (!adyenNotificationValidator.isValidHmac(hmacSignature, hmacKey, payload)) {
                LOGGER.atInfo().setMessage("Hmac signature is invalid, rejecting Adyen token notification")
                        .addKeyValue(PROVIDER, ADYEN.getName()).log();
                return false;
            }

            return true;

        } catch (AdyenNotificationException e) {
            logValidationFailure("token", e);
            return false;
        }

    }

    public <T> T deserialiseTokenPayload(String payload, Class<T> targetClass) throws AdyenNotificationException {
        try {
            return jsonObjectMapper.getObject(payload, targetClass);
        } catch (Exception e) {
            LOGGER.info("Error deserialising token notification payload", e);
            throw new WebApplicationException("Error deserialising token webhook Json", e);
        }
    }
    private void logValidationFailure(String notificationType, AdyenNotificationException e) {
        LOGGER.error(
                "Failed to validate Adyen {} notification payload",
                notificationType,
                e
        );
    }
}
