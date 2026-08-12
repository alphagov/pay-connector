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
import static uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationValidator.NOTIFICATION_SOURCE;
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

    public boolean handleNotificationFor(String payload, String headerHmacSignature, String forwardedIpAddresses) {

        if (!adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses)) {
            return false;
        }
        try {
            return headerHmacSignature != null
                    ? handleTokenNotifications(payload, headerHmacSignature, forwardedIpAddresses)
                    : handlePaymentNotifications(payload, forwardedIpAddresses);
        } catch (AdyenNotificationException e) {
            LOGGER.error("Failed to validate Adyen notification payload", e);
            return false;
        }
    }

    private boolean handlePaymentNotifications(String payload, String forwardedIpAddresses) {
        var notificationRequest = deserialisePaymentPayloadToNotificationRequest(payload);
        List<NotificationRequestItem> items = extractNotificationItems(notificationRequest);
        boolean live = "true".equalsIgnoreCase(notificationRequest.getLive());

        for (NotificationRequestItem item : items) { // you only need to loop for payment webhooks
            String hmacKey = getHmacKey(adyenGatewayConfig, live);
            if (!adyenNotificationValidator.isValidHmac(item, hmacKey)) {
                logFailedValidation(forwardedIpAddresses);
                return false;
            }

            if (AdyenPaymentEvent.contains(item.getEventCode())) {
                addNotificationToTaskQueue(payload, item.getEventCode(), item.getPspReference(), TaskType.HANDLE_ADYEN_PAYMENTS_WEBHOOK_NOTIFICATION);
                continue;
            }

            logIgnoreWebhookNotification(item.getEventCode(), notificationRequest.getLive(), item.getPspReference());
        }
        logSuccessNotificationProcessed(forwardedIpAddresses);
        return true;
    }

    private boolean handleTokenNotifications(String payload, String headerHmacSignature, String forwardedIpAddresses) {
        var notificationRequest = deserialiseTokenPayloadToNotificationRequest(payload);
        boolean live = notificationRequest.environment().equals("live");
        String hmacKey = getTokenHmacKey(adyenGatewayConfig, live);

        if (!adyenNotificationValidator.isValidHmac(headerHmacSignature, hmacKey, payload)) {
            logFailedValidation(forwardedIpAddresses);
            return false;
        }

        if (!AdyenPaymentEvent.contains(notificationRequest.type())) {
            logIgnoreWebhookNotification(notificationRequest.type(),
                    notificationRequest.environment(),
                    notificationRequest.data().shopperReference());
            return true;
        }

        addNotificationToTaskQueue(payload, notificationRequest.type(), notificationRequest.data().shopperReference(), TaskType.HANDLE_ADYEN_TOKEN_WEBHOOK_NOTIFICATION);
        logSuccessNotificationProcessed(forwardedIpAddresses);
        return true;
    }


    private void addNotificationToTaskQueue(String payload, String event, String pspReference, TaskType taskType) {
        try {
            taskQueueService.add(new Task(payload, taskType));
        } catch (Exception e) {
            LOGGER.error("Error sending Adyen webhook notification to task SQS queue",
                    kv("pspReference", pspReference),
                    kv("eventCode", event),
                    e);
            throw new WebApplicationException(
                    "Error sending message to task SQS queue",
                    e);
        }
    }

    public NotificationRequest deserialisePaymentPayloadToNotificationRequest(String rawAdyenJson) {
        try {
            WebhookHandler webhookHandler = new WebhookHandler();
            return webhookHandler.handleNotificationJson(rawAdyenJson);
        } catch (Exception e) {
            LOGGER.info("Error deserialising Adyen notification payload", e);
            throw new WebApplicationException("Error deserialising webhook Json", e);
        }
    }

    public AdyenTokenNotification deserialiseTokenPayloadToNotificationRequest(String payload) throws AdyenNotificationException {
        try {
            return jsonObjectMapper.getObject(payload, AdyenTokenNotification.class);
        } catch (Exception e) {
            LOGGER.info("Error deserialising token notification payload", e);
            throw new WebApplicationException("Error deserialising token webhook Json", e);
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

    public void logIgnoreWebhookNotification(String type, String environment, String reference) {
        LOGGER.atInfo()
                .setMessage("Ignoring unsupported Adyen notification")
                .addKeyValue("type", type)
                .addKeyValue("reference", reference)
                .addKeyValue("environment", environment)
                .log();

    }
    
    public void logFailedValidation(String forwardedIpAddresses) {
        LOGGER.atInfo()
                .setMessage("Rejecting invalid Adyen notification")
                .addKeyValue(PROVIDER, ADYEN.getName())
                .addKeyValue(NOTIFICATION_SOURCE, forwardedIpAddresses)
                .log();
    }

    public void logSuccessNotificationProcessed(String forwardedIpAddresses) {
        LOGGER.info("Processed Adyen notification",
                kv(PROVIDER, ADYEN.getName()),
                kv(NOTIFICATION_SOURCE, forwardedIpAddresses));
    }
}
