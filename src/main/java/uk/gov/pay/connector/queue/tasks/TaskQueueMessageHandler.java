package uk.gov.pay.connector.queue.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.queue.tasks.handlers.AuthoriseWithUserNotPresentHandler;
import uk.gov.pay.connector.queue.tasks.handlers.CollectFeesForFailedPaymentsTaskHandler;
import uk.gov.pay.connector.queue.tasks.handlers.DeleteStoredPaymentDetailsTaskHandler;
import uk.gov.pay.connector.queue.tasks.handlers.QueryAndUpdatePaymentInSubmittedStateTaskHandler;
import uk.gov.pay.connector.queue.tasks.handlers.RetryPaymentOrRefundEmailTaskHandler;
import uk.gov.pay.connector.queue.tasks.handlers.ServiceArchivedTaskHandler;
import uk.gov.pay.connector.queue.tasks.handlers.StripeWebhookTaskHandler;
import uk.gov.pay.connector.queue.tasks.model.DeleteStoredPaymentDetailsTaskData;
import uk.gov.pay.connector.queue.tasks.model.PaymentTaskData;
import uk.gov.pay.connector.queue.tasks.model.RetryPaymentOrRefundEmailTaskData;
import uk.gov.pay.connector.queue.tasks.model.ServiceArchivedTaskData;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import javax.inject.Inject;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.service.payments.logging.LoggingKeys.AGREEMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_INSTRUMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.RESOURCE_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.SERVICE_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.STRIPE_EVENT_ID;

public class TaskQueueMessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskQueueMessageHandler.class);
    private final TaskQueue taskQueue;
    private final CollectFeesForFailedPaymentsTaskHandler collectFeesForFailedPaymentsTaskHandler;
    private final StripeWebhookTaskHandler stripeWebhookTaskHandler;
    private final AuthoriseWithUserNotPresentHandler authoriseWithUserNotPresentHandler;
    private final DeleteStoredPaymentDetailsTaskHandler deleteStoredPaymentDetailsHandler;
    private final RetryPaymentOrRefundEmailTaskHandler retryPaymentOrRefundEmailTaskHandler;
    private ServiceArchivedTaskHandler serviceArchivedTaskHandler;
    private final QueryAndUpdatePaymentInSubmittedStateTaskHandler queryAndUpdatePaymentInSubmittedStateTaskHandler;
    private final ObjectMapper objectMapper;

    @Inject
    public TaskQueueMessageHandler(TaskQueue taskQueue,
                                   CollectFeesForFailedPaymentsTaskHandler collectFeesForFailedPaymentsTaskHandler,
                                   StripeWebhookTaskHandler stripeWebhookTaskHandler,
                                   AuthoriseWithUserNotPresentHandler authoriseWithUserNotPresentHandler,
                                   DeleteStoredPaymentDetailsTaskHandler deleteStoredPaymentDetailsHandler,
                                   RetryPaymentOrRefundEmailTaskHandler retryPaymentOrRefundEmailTaskHandler,
                                   ServiceArchivedTaskHandler serviceArchivedTaskHandler,
                                   QueryAndUpdatePaymentInSubmittedStateTaskHandler queryAndUpdatePaymentInSubmittedStateTaskHandler,
                                   ObjectMapper objectMapper) {
        this.taskQueue = taskQueue;
        this.collectFeesForFailedPaymentsTaskHandler = collectFeesForFailedPaymentsTaskHandler;
        this.stripeWebhookTaskHandler = stripeWebhookTaskHandler;
        this.authoriseWithUserNotPresentHandler = authoriseWithUserNotPresentHandler;
        this.deleteStoredPaymentDetailsHandler = deleteStoredPaymentDetailsHandler;
        this.retryPaymentOrRefundEmailTaskHandler = retryPaymentOrRefundEmailTaskHandler;
        this.serviceArchivedTaskHandler = serviceArchivedTaskHandler;
        this.queryAndUpdatePaymentInSubmittedStateTaskHandler = queryAndUpdatePaymentInSubmittedStateTaskHandler;
        this.objectMapper = objectMapper;
    }

    public void processMessages() throws QueueException {
        List<TaskMessage> taskMessages = taskQueue.retrieveTaskQueueMessages();
        taskMessages.forEach(taskMessage -> {
            try {
                LOGGER.info("Processing message from queue",
                        kv("queueMessageId", taskMessage.getQueueMessageId()),
                        kv("queueMessageReceiptHandle", taskMessage.getQueueMessageReceiptHandle())
                );
                var taskType = taskMessage.getTask().getTaskType();

                switch (taskType) {
                    case COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT:
                        PaymentTaskData paymentTaskData;
                        // backport existing task message format - will be removed
                        if (taskMessage.getTask().getPaymentExternalId() != null) {
                            paymentTaskData = new PaymentTaskData(taskMessage.getTask().getPaymentExternalId());
                        } else {
                            paymentTaskData = objectMapper.readValue(taskMessage.getTask().getData(), PaymentTaskData.class);
                        }
                        MDC.put(PAYMENT_EXTERNAL_ID, paymentTaskData.getPaymentExternalId());
                        LOGGER.info("Processing [{}] task.", taskType.getName());
                        collectFeesForFailedPaymentsTaskHandler.collectAndPersistFees(paymentTaskData);
                        break;
                    case HANDLE_STRIPE_WEBHOOK_NOTIFICATION:
                        var stripeNotification = objectMapper.readValue(taskMessage.getTask().getData(), StripeNotification.class);
                        MDC.put(STRIPE_EVENT_ID, stripeNotification.getId());
                        LOGGER.info("Processing [{}] task.", taskType.getName());
                        stripeWebhookTaskHandler.process(stripeNotification);
                        break;
                    case AUTHORISE_WITH_USER_NOT_PRESENT:
                        var taskData = objectMapper.readValue(taskMessage.getTask().getData(), PaymentTaskData.class);
                        MDC.put(PAYMENT_EXTERNAL_ID, taskData.getPaymentExternalId());
                        LOGGER.info("Processing [{}] task.", taskType.getName());
                        authoriseWithUserNotPresentHandler.process(taskData.getPaymentExternalId());
                        break;
                    case DELETE_STORED_PAYMENT_DETAILS:
                        var deleteStoredPaymentDetailsTaskData = objectMapper.readValue(taskMessage.getTask().getData(), DeleteStoredPaymentDetailsTaskData.class);
                        MDC.put(AGREEMENT_EXTERNAL_ID, deleteStoredPaymentDetailsTaskData.getAgreementExternalId());
                        MDC.put(PAYMENT_INSTRUMENT_EXTERNAL_ID, deleteStoredPaymentDetailsTaskData.getPaymentInstrumentExternalId());
                        LOGGER.info("Processing [{}] task.", taskType.getName());
                        deleteStoredPaymentDetailsHandler.process(deleteStoredPaymentDetailsTaskData.getAgreementExternalId(), deleteStoredPaymentDetailsTaskData.getPaymentInstrumentExternalId());
                        break;
                    case RETRY_FAILED_PAYMENT_OR_REFUND_EMAIL:
                        var retryPaymentOrRefundEmailTaskData = objectMapper.readValue(taskMessage.getTask().getData(), RetryPaymentOrRefundEmailTaskData.class);
                        MDC.put(RESOURCE_EXTERNAL_ID, retryPaymentOrRefundEmailTaskData.getResourceExternalId());
                        MDC.put("email_notification_type", retryPaymentOrRefundEmailTaskData.getEmailNotificationType().toString());
                        LOGGER.info("Processing [{}] task.", taskType.getName());
                        retryPaymentOrRefundEmailTaskHandler.process(retryPaymentOrRefundEmailTaskData);
                        break;
                    case SERVICE_ARCHIVED:
                        var serviceArchivedTaskData = objectMapper.readValue(taskMessage.getTask().getData(), ServiceArchivedTaskData.class);
                        MDC.put(SERVICE_EXTERNAL_ID, serviceArchivedTaskData.getServiceId());
                        LOGGER.info("Processing [{}] task.", taskType.getName());
                        serviceArchivedTaskHandler.process(serviceArchivedTaskData);
                        break;
                    case QUERY_AND_UPDATE_CAPTURE_SUBMITTED_PAYMENT:
                        var data = objectMapper.readValue(taskMessage.getTask().getData(), PaymentTaskData.class);
                        MDC.put(PAYMENT_EXTERNAL_ID, data.getPaymentExternalId());
                        LOGGER.info("Processing [{}] task.", taskType.getName());
                        queryAndUpdatePaymentInSubmittedStateTaskHandler.process(data);
                        break;
                    default:
                        LOGGER.error("Task [{}] is not supported.", taskType.getName());
                }
                taskQueue.markMessageAsProcessed(taskMessage.getQueueMessage());
                LOGGER.info("Successfully processed [{}] task.", taskType.getName());
            } catch (Exception e) {
                LOGGER.error("Error processing message from queue",
                        kv("queueMessageId", taskMessage.getQueueMessageId()),
                        kv("errorMessage", e.getMessage())
                );
                Sentry.captureException(e);
            } finally {
                MDC.remove(PAYMENT_EXTERNAL_ID);
                MDC.remove(STRIPE_EVENT_ID);
                MDC.remove(AGREEMENT_EXTERNAL_ID);
                MDC.remove(PAYMENT_INSTRUMENT_EXTERNAL_ID);
                MDC.remove(RESOURCE_EXTERNAL_ID);
                MDC.remove("email_notification_type");
            }
        });
    }
}
