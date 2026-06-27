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
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil;
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.queue.tasks.TaskType;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.pay.connector.util.IpDomainMatcher;

import java.security.SignatureException;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenNotificationService.class);

    private final AdyenGatewayConfig adyenGatewayConfig;
    private final IpDomainMatcher ipDomainMatcher;
    private final HMACValidator hmacValidator;
    private final TaskQueueService taskQueueService;

    @Inject
    public AdyenNotificationService(AdyenGatewayConfig adyenGatewayConfig, IpDomainMatcher ipDomainMatcher, TaskQueueService taskQueueService) {
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.ipDomainMatcher = ipDomainMatcher;
        this.taskQueueService = taskQueueService;
        this.hmacValidator = new HMACValidator();
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

        try {
            NotificationRequest notificationRequest = deserialisePayloadToNotificationRequest(payload);
            List<NotificationRequestItem> items = extractNotificationItem(notificationRequest);

            boolean live = "true".equalsIgnoreCase(notificationRequest.getLive());

            String hmacKey = AdyenConfigUtil.getHmacKey(adyenGatewayConfig, live);

            for (NotificationRequestItem item : items) {
                if (!isValidHmac(item, hmacKey)) {
                    return false;
                }
                
                // Ignore payment transfer/payout events
                if (isPaymentTransferEvent(item.getEventCode())) {
                    LOGGER.info("Ignored Adyen payment transfer event",
                            kv("originalReference", item.getOriginalReference()),
                            kv("eventCode", item.getEventCode()));
                    continue;
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

        LOGGER.info("Processed Adyen notification",
                kv(PROVIDER, PaymentGatewayName.ADYEN.getName()),
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

    public List<NotificationRequestItem> extractNotificationItem(NotificationRequest notificationRequest) {
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

    private boolean isPaymentTransferEvent(String eventCode) {
        // Ignore payment transfer/payout related events
        return eventCode.equals("ACCOUNT_HOLDER_PAYOUT") ||
                eventCode.equals("TRANSFER") ||
                eventCode.equals("BANK_ACCOUNT_VERIFICATION") ||
                eventCode.equals("SCHEDULED_TRANSFER") ||
                eventCode.startsWith("ACCOUNT_HOLDER_") ||
                eventCode.startsWith("BALANCE_ACCOUNT_") ||
                eventCode.startsWith("PAYMENT_INSTRUMENT_");
    }
}
