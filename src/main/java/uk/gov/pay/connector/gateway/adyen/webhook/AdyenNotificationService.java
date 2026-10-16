package uk.gov.pay.connector.gateway.adyen.webhook;

import com.google.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookNotification;
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.queue.tasks.TaskType;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenBalanceReportNotificationHandler;
import uk.gov.pay.connector.queue.tasks.model.Task;

import java.util.List;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookType.BALANCE_PLATFORM_REPORT;
import static uk.gov.pay.connector.queue.tasks.TaskType.HANDLE_ADYEN_CONFIGURATION_WEBHOOK_NOTIFICATION;
import static uk.gov.pay.connector.queue.tasks.TaskType.HANDLE_ADYEN_PAYMENTS_WEBHOOK_NOTIFICATION;
import static uk.gov.pay.connector.queue.tasks.TaskType.HANDLE_ADYEN_TOKEN_WEBHOOK_NOTIFICATION;
import static uk.gov.pay.connector.queue.tasks.TaskType.HANDLE_ADYEN_TRANSFER_WEBHOOK_NOTIFICATION;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class AdyenNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenNotificationService.class);
    private final TaskQueueService taskQueueService;
    private final AdyenNotificationValidator adyenNotificationValidator;
    private final AdyenWebhookNotificationParser adyenWebhookNotificationParser;
    private final AdyenBalanceReportNotificationHandler adyenBalanceReportNotificationHandler;

    private static final String LOGGING_KEY_ADYEN_EVENT = "adyen_event";
    private static final String LOGGING_KEY_ADYEN_EVENT_CODE_OR_TYPE = "adyen_event_code_or_type";
    private static final String LOGGING_KEY_ADYEN_ENVIRONMENT = "adyen_environment";

    @Inject
    public AdyenNotificationService(TaskQueueService taskQueueService, AdyenNotificationValidator adyenNotificationValidator,
                                    AdyenWebhookNotificationParser adyenWebhookNotificationParser,
                                    AdyenBalanceReportNotificationHandler adyenBalanceReportNotificationHandler) {
        this.taskQueueService = taskQueueService;
        this.adyenNotificationValidator = adyenNotificationValidator;
        this.adyenWebhookNotificationParser = adyenWebhookNotificationParser;
        this.adyenBalanceReportNotificationHandler = adyenBalanceReportNotificationHandler;
    }

    public boolean handleNotificationFor(String payload, String forwardedIpAddresses, String hmacSignature) {
        if (!adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses)) {
            return false;
        }
        try {
            MDC.put(PROVIDER, ADYEN.getName());

            AdyenWebhookNotification adyenWebhookNotification = adyenWebhookNotificationParser.parse(payload);

            MDC.put(LOGGING_KEY_ADYEN_EVENT, adyenWebhookNotification.event().name());
            MDC.put(LOGGING_KEY_ADYEN_EVENT_CODE_OR_TYPE, adyenWebhookNotification.event().getEventCodeOrType());
            MDC.put(LOGGING_KEY_ADYEN_ENVIRONMENT, adyenWebhookNotification.environment().name());

            if (adyenWebhookNotification.event().isIgnored()) {
                LOGGER.atInfo()
                        .setMessage("Adyen webhook notification has been ignored")
                        .log();
                return true;
            }

            boolean validHmacSignature = adyenNotificationValidator.validateHmacSignature(adyenWebhookNotification, payload, hmacSignature);

            if (!validHmacSignature) {
                return false;
            }

            if (BALANCE_PLATFORM_REPORT.equals(adyenWebhookNotification.event().getWebhookType())) {
                adyenBalanceReportNotificationHandler.process(payload);
            } else {
                addNotificationToTaskQueue(adyenWebhookNotification, payload);
            }

            LOGGER.atInfo()
                    .setMessage("Processed Adyen notification")
                    .addKeyValue("notification_source", forwardedIpAddresses)
                    .log();

            return true;
        } catch (AdyenNotificationException e) {
            LOGGER.atWarn()
                    .setMessage("Failed to process Adyen notification - {}")
                    .addArgument(e.getMessage())
                    .setCause(e)
                    .log();
            return false;
        } catch (UnparseableAdyenWebhookException e) {
            LOGGER.atWarn()
                    .setMessage("Unabled to parse Adyen notification - {}")
                    .addArgument(e.getMessage())
                    .setCause(e)
                    .log();
            throw new WebApplicationException("Error parsing Adyen notification");
        } catch (UnrecognisedWebhookEventException e) {
            LOGGER.atError()
                    .setMessage("Unknown Adyen webhook event")
                    .setCause(e)
                    .log();
            return true;
        } finally {
            List.of(LOGGING_KEY_ADYEN_EVENT, LOGGING_KEY_ADYEN_EVENT_CODE_OR_TYPE,
                            LOGGING_KEY_ADYEN_ENVIRONMENT,
                            PROVIDER)
                    .forEach(MDC::remove);
        }
    }

    private void addNotificationToTaskQueue(AdyenWebhookNotification adyenWebhookNotification, String payload) {
        TaskType taskTypeForNotification = getTaskTypeForAdyenNotification(adyenWebhookNotification);
        try {
            taskQueueService.add(new Task(payload, taskTypeForNotification));
        } catch (Exception e) {
            LOGGER.atError()
                    .setMessage("Error sending Adyen webhook notification to task SQS queue")
                    .setCause(e)
                    .log();
            throw new WebApplicationException("Error sending message to task SQS queue");
        }
    }

    private TaskType getTaskTypeForAdyenNotification(AdyenWebhookNotification adyenWebhookNotification) {
        return switch (adyenWebhookNotification.event().getWebhookType()) {
            case PAYMENTS -> HANDLE_ADYEN_PAYMENTS_WEBHOOK_NOTIFICATION;
            case TOKENS -> HANDLE_ADYEN_TOKEN_WEBHOOK_NOTIFICATION;
            case TRANSFER -> HANDLE_ADYEN_TRANSFER_WEBHOOK_NOTIFICATION;
            case CONFIGURATION -> HANDLE_ADYEN_CONFIGURATION_WEBHOOK_NOTIFICATION;
            case BALANCE_PLATFORM_REPORT -> {
                LOGGER.atError()
                        .setMessage("Balance platform reports are not expected to be processed by the task queue")
                        .log();
                yield null;
            }
        };
    }
}
