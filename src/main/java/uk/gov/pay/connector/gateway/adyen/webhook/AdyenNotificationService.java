package uk.gov.pay.connector.gateway.adyen.webhook;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.notification.WebhookHandler;
import com.adyen.util.HMACValidator;
import com.google.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil;
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.queue.tasks.TaskType;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.pay.connector.util.IpDomainMatcher;

import java.security.SignatureException;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class AdyenNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenNotificationService.class);

    private final AdyenGatewayConfig adyenGatewayConfig;
    private final HMACValidator hmacValidator;
    private final TaskQueueService taskQueueService;
    private final AdyenNotificationValidator adyenNotificationValidator;

    @Inject
    public AdyenNotificationService(AdyenGatewayConfig adyenGatewayConfig, IpDomainMatcher ipDomainMatcher, TaskQueueService taskQueueService) {
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.taskQueueService = taskQueueService;
        this.hmacValidator = new HMACValidator();
        this.adyenNotificationValidator = new AdyenNotificationValidator(ipDomainMatcher);
    }

    public boolean handleNotificationFor(String payload, String forwardedIpAddresses) {
        String notificationDomain = adyenGatewayConfig.getNotificationDomain();

        if (!adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses, notificationDomain)) {
            return false;
        }

        try {
            NotificationRequest notificationRequest = deserialisePayloadToNotificationRequest(payload);
            List<NotificationRequestItem> items = extractNotificationItems(notificationRequest);

            boolean live = "true".equalsIgnoreCase(notificationRequest.getLive());

            String hmacKey = AdyenConfigUtil.getHmacKey(adyenGatewayConfig, live);

            for (NotificationRequestItem item : items) {
                if (!isValidHmac(item, hmacKey)) {
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
            LOGGER.error("Failed to validate Adyen notification payload", e);
            return false;
        }

        adyenNotificationValidator.logSuccessfulValidation(forwardedIpAddresses);

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

    private boolean isValidHmac(NotificationRequestItem item, String hmacKey) {
        try {
            boolean validSignature = hmacValidator.validateHMAC(item, hmacKey);

            if (!validSignature) {
                LOGGER.error("Invalid HMAC signature in the payload for Adyen notification",
                        kv("pspReference", item.getPspReference()),
                        kv("eventCode", item.getEventCode()));
            }
            return validSignature;
        } catch (IllegalArgumentException | SignatureException e) {
            LOGGER.info("Failed to validate HMAC signature",
                    kv("pspReference", item.getPspReference()),
                    kv("eventCode", item.getEventCode()));
            throw new AdyenNotificationException("Failed to validate HMAC signature", e);
        }
    }
}
