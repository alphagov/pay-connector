package uk.gov.pay.connector.queue.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentry.Sentry;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.queue.tasks.model.PaymentTaskData;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import javax.inject.Inject;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class TaskQueueService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskQueue taskQueue;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final ObjectMapper objectMapper;


    @Inject
    public TaskQueueService(TaskQueue taskQueue,
                            StripeGatewayConfig stripeGatewayConfig,
                            ObjectMapper objectMapper) {
        this.taskQueue = taskQueue;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.objectMapper = objectMapper;
    }

    public void offerTasksOnStateTransition(ChargeEntity chargeEntity) {
        boolean isTerminallyFailed = chargeEntity.getChargeStatus().isExpungeable() &&
                chargeEntity.getChargeStatus().toExternal() != ExternalChargeState.EXTERNAL_SUCCESS;

        if (isTerminallyFailed && chargeEntity.getPaymentGatewayName() == PaymentGatewayName.STRIPE &&
                !isEmpty(chargeEntity.getGatewayTransactionId()) &&
                chargeEntity.getFees().isEmpty()) {
            addCollectStripeFeeForFailedPaymentTask(chargeEntity);
        } else if (chargeEntity.getChargeStatus() == ChargeStatus.AUTHORISATION_USER_NOT_PRESENT_QUEUED) {
            addAuthoriseUserNotPresentTask(chargeEntity);
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

    private void addAuthoriseUserNotPresentTask(ChargeEntity chargeEntity) {
        try {
            var data = new PaymentTaskData(chargeEntity.getExternalId());
            add(new Task(objectMapper.writeValueAsString(data), TaskType.AUTHORISE_USER_NOT_PRESENT));
        } catch (Exception e) {
            logger.warn("Error adding payment task message to queue", ArrayUtils.addAll(
                    chargeEntity.getStructuredLoggingArgs(),
                    kv("task_name", TaskType.AUTHORISE_USER_NOT_PRESENT),
                    kv("error", e.getMessage()))
            );
            Sentry.captureException(e);
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
}
