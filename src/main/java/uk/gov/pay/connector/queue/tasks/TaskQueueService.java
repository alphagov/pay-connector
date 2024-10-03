package uk.gov.pay.connector.queue.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentry.Sentry;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.queue.tasks.model.DeleteStoredPaymentDetailsTaskData;
import uk.gov.pay.connector.queue.tasks.model.PaymentTaskData;
import uk.gov.pay.connector.queue.tasks.model.RetryPaymentOrRefundEmailTaskData;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import javax.inject.Inject;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static uk.gov.pay.connector.queue.tasks.TaskType.RETRY_FAILED_PAYMENT_OR_REFUND_EMAIL;
import static uk.gov.service.payments.logging.LoggingKeys.AGREEMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_INSTRUMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.RESOURCE_EXTERNAL_ID;

public class TaskQueueService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TaskQueue taskQueue;
    private final ObjectMapper objectMapper;
    private final int maxAllowedDeliveryDelay;

    @Inject
    public TaskQueueService(TaskQueue taskQueue,
                            ObjectMapper objectMapper,
                            ConnectorConfiguration connectorConfiguration) {
        this.taskQueue = taskQueue;
        this.objectMapper = objectMapper;
        maxAllowedDeliveryDelay = connectorConfiguration.getSqsConfig().getMaxAllowedDeliveryDelayInSeconds();
    }

    public void offerTasksOnStateTransition(ChargeEntity chargeEntity) {
        boolean isTerminallyFailed = chargeEntity.getChargeStatus().isExpungeable() &&
                chargeEntity.getChargeStatus().toExternal() != ExternalChargeState.EXTERNAL_SUCCESS;

        if (isTerminallyFailed && chargeEntity.getPaymentGatewayName() == PaymentGatewayName.STRIPE &&
                !isEmpty(chargeEntity.getGatewayTransactionId()) &&
                chargeEntity.getFees().isEmpty()) {
            addCollectStripeFeeForFailedPaymentTask(chargeEntity);
        }
    }

    public void add(Task task) {
        try {
            taskQueue.addTaskToQueue(task);
        } catch (QueueException | JsonProcessingException e) {
            logger.error("Error adding task to queue",
                    kv("task_name", task.getTaskType().getName()),
                    kv("error", e.getMessage()));
        }
    }

    public void addAuthoriseWithUserNotPresentTask(ChargeEntity chargeEntity) {
        try {
            MDC.put(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId());
            var data = new PaymentTaskData(chargeEntity.getExternalId());
            add(new Task(objectMapper.writeValueAsString(data), TaskType.AUTHORISE_WITH_USER_NOT_PRESENT));
        } catch (Exception e) {
            logger.warn("Error adding payment task message to queue", ArrayUtils.addAll(
                    chargeEntity.getStructuredLoggingArgs(),
                    kv("task_name", TaskType.AUTHORISE_WITH_USER_NOT_PRESENT),
                    kv("error", e.getMessage()))
            );
            Sentry.captureException(e);
        } finally {
            MDC.remove(PAYMENT_EXTERNAL_ID);
        }
    }

    public void addDeleteStoredPaymentDetailsTask(AgreementEntity agreementEntity, PaymentInstrumentEntity paymentInstrumentEntity) {
        try {
            MDC.put(AGREEMENT_EXTERNAL_ID, agreementEntity.getExternalId());
            MDC.put(PAYMENT_INSTRUMENT_EXTERNAL_ID, paymentInstrumentEntity.getExternalId());

            var data = new DeleteStoredPaymentDetailsTaskData(agreementEntity.getExternalId(), paymentInstrumentEntity.getExternalId());
            add(new Task(objectMapper.writeValueAsString(data), TaskType.DELETE_STORED_PAYMENT_DETAILS));
        } catch (Exception e) {
            logger.warn("Error adding agreement task message to queue", ArrayUtils.addAll(
                    agreementEntity.getStructuredLoggingArgs(),
                    kv("task_name", TaskType.DELETE_STORED_PAYMENT_DETAILS),
                    kv("error", e.getMessage()))
            );
            Sentry.captureException(e);
        } finally {
            MDC.remove(AGREEMENT_EXTERNAL_ID);
            MDC.remove(PAYMENT_INSTRUMENT_EXTERNAL_ID);
        }
    }

    private void addCollectStripeFeeForFailedPaymentTask(ChargeEntity chargeEntity) {
        try {
            MDC.put(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId());
            String data = objectMapper.writeValueAsString(new PaymentTaskData(chargeEntity.getExternalId()));
            Task task = new Task(data, TaskType.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT);
            taskQueue.addTaskToQueue(task);
            logger.info("Added payment task message to queue", ArrayUtils.addAll(
                    chargeEntity.getStructuredLoggingArgs(),
                    kv("task_name", TaskType.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT)
            ));
        } catch (Exception e) {
            logger.warn("Error adding payment task message to queue", ArrayUtils.addAll(
                    chargeEntity.getStructuredLoggingArgs(),
                    kv("task_name", TaskType.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT),
                    kv("error", e.getMessage()))
            );
            Sentry.captureException(e);
        } finally {
            MDC.remove(PAYMENT_EXTERNAL_ID);
        }
    }

    public void addRetryFailedPaymentOrRefundEmailTask(RetryPaymentOrRefundEmailTaskData taskData) {
        try {
            MDC.put(RESOURCE_EXTERNAL_ID, taskData.getResourceExternalId());
            MDC.put("email_notification_type", taskData.getEmailNotificationType().name());

            String data = objectMapper.writeValueAsString(taskData);
            Task task = new Task(data, RETRY_FAILED_PAYMENT_OR_REFUND_EMAIL);
            taskQueue.addTaskToQueue(task, maxAllowedDeliveryDelay);

            logger.info("Added retry failed payment or refund email task message to queue");
        } catch (Exception e) {
            logger.error("Error adding failed payment or refund email task message to queue",
                    kv("error", e.getMessage()));
            Sentry.captureException(e);
        } finally {
            MDC.remove(RESOURCE_EXTERNAL_ID);
            MDC.remove("email_notification_type");
        }
    }

    public void addQueryAndUpdateChargeInSubmittedStateTask(ChargeEntity chargeEntity) {
        try {
            MDC.put(PAYMENT_EXTERNAL_ID, chargeEntity.getExternalId());
            var data = new PaymentTaskData(chargeEntity.getExternalId());
            add(new Task(objectMapper.writeValueAsString(data), TaskType.QUERY_AND_UPDATE_CAPTURE_SUBMITTED_PAYMENT));
        } catch (Exception e) {
            logger.warn("Error adding payment task message to queue", ArrayUtils.addAll(
                    chargeEntity.getStructuredLoggingArgs(),
                    kv("task_name", TaskType.QUERY_AND_UPDATE_CAPTURE_SUBMITTED_PAYMENT),
                    kv("error", e.getMessage()))
            );
            Sentry.captureException(e);
        } finally {
            MDC.remove(PAYMENT_EXTERNAL_ID);
        }
    }

}
