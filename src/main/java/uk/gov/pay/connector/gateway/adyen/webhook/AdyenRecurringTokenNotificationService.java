package uk.gov.pay.connector.gateway.adyen.webhook;

import jakarta.inject.Inject;
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

import java.util.Set;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getTokenHmacKey;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenRecurringTokenNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenRecurringTokenNotificationService.class);
    private final AdyenNotificationValidator adyenNotificationValidator;
    private static final String NOTIFICATION_SOURCE = "notification_source";
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final JsonObjectMapper jsonObjectMapper;
    private final  TaskQueueService taskQueueService;
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "recurring.token.created",
            "recurring.token.disabled"
    );

    @Inject
    public AdyenRecurringTokenNotificationService(AdyenGatewayConfig adyenGatewayConfig,
                                                  AdyenNotificationValidator adyenNotificationValidator,
                                                  JsonObjectMapper jsonObjectMapper,
                                                  TaskQueueService taskQueueService) {
        this.adyenNotificationValidator = adyenNotificationValidator;
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.jsonObjectMapper = jsonObjectMapper;
        this.taskQueueService = taskQueueService;
    }

    public boolean handleNotificationFor(String payload, String hmacSignature, String forwardedIpAddresses) {
        try {
            if (!adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses)) {
                return false;
            }

            if (hmacSignature == null || hmacSignature.isBlank()) {
                LOGGER.atInfo()
                        .setMessage("Hmac signature is missing, rejecting Adyen token notification")
                        .addKeyValue(PROVIDER, ADYEN.getName())
                        .log();
                return false;
            }

            AdyenTokenNotification adyenTokenResponse = deserialisePayload(payload, AdyenTokenNotification.class);

            boolean live = adyenTokenResponse.environment().equalsIgnoreCase("live");
            String hmacKey = getTokenHmacKey(adyenGatewayConfig, live);
            if (!adyenNotificationValidator.isValidHmac(hmacSignature, hmacKey, payload)) {
                LOGGER.atInfo()
                        .setMessage("Hmac signature is invalid, rejecting Adyen token notification")
                        .addKeyValue(PROVIDER, ADYEN.getName())
                        .log();
                return false;
            }

            if (!isSupportedEventType(adyenTokenResponse.type())) {
                LOGGER.atInfo()
                        .setMessage("Ignoring unsupported Adyen token notification")
                        .addKeyValue("type", adyenTokenResponse.type())
                        .addKeyValue("shopperReference", adyenTokenResponse.data().shopperReference())
                        .addKeyValue("environment", adyenTokenResponse.environment())
                        .addKeyValue("eventId", adyenTokenResponse.eventId())
                        .log();

                return true;
            }
            addNotificationToTaskQueue(payload);
            LOGGER.atInfo()
                    .setMessage("Processed Adyen token notification")
                    .addKeyValue(PROVIDER, ADYEN.getName())
                    .addKeyValue(NOTIFICATION_SOURCE, forwardedIpAddresses)
                    .log();
            return true;
        } catch (AdyenNotificationException e) {
            LOGGER.error("Failed to validate Adyen token notification payload", e);
            return false;
        }
    }
    
    public <T> T deserialisePayload(String payload, Class<T> targetClass) throws AdyenNotificationException {
        try {
            return jsonObjectMapper.getObject(payload, targetClass);
        } catch (Exception e) {
            LOGGER.info("Error deserialising token notification payload", e);
            throw new WebApplicationException("Error deserialising token webhook Json", e);
        }
    }
    private boolean isSupportedEventType(String eventType) {
        return SUPPORTED_EVENT_TYPES.contains(eventType);
    }
    
    private void addNotificationToTaskQueue(String payload) {
        try {
            taskQueueService.add(
                    new Task(payload, TaskType.HANDLE_ADYEN_TOKEN_WEBHOOK_NOTIFICATION)
            );
        } catch (Exception e) {
            LOGGER.error("Error sending Adyen token webhook notification to task SQS queue", e);
            
            throw new WebApplicationException("Error sending message to task SQS queue", e);
        }
    }
}



