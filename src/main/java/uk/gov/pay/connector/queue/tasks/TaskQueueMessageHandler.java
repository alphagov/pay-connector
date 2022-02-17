package uk.gov.pay.connector.queue.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import javax.inject.Inject;
import java.util.List;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.queue.tasks.TaskQueueService.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class TaskQueueMessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskQueueMessageHandler.class);
    private final TaskQueue taskQueue;
    private final CollectFeesForFailedPaymentsTaskHandler collectFeesForFailedPaymentsTaskHandler;

    @Inject
    public TaskQueueMessageHandler(TaskQueue taskQueue,
                                   CollectFeesForFailedPaymentsTaskHandler collectFeesForFailedPaymentsTaskHandler) {
        this.taskQueue = taskQueue;
        this.collectFeesForFailedPaymentsTaskHandler = collectFeesForFailedPaymentsTaskHandler;
    }

    public void processMessages() throws QueueException {
        List<PaymentTaskMessage> paymentTaskMessages = taskQueue.retrieveTaskQueueMessages();
        for (PaymentTaskMessage paymentTaskMessage : paymentTaskMessages) {
            try {
                LOGGER.info("Processing task from queue",
                        kv("queueMessageId", paymentTaskMessage.getQueueMessageId()),
                        kv("queueMessageReceiptHandle", paymentTaskMessage.getQueueMessageReceiptHandle()),
                        kv(PAYMENT_EXTERNAL_ID, paymentTaskMessage.getPaymentExternalId())
                );

                if (paymentTaskMessage.getTask().equals(COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT_TASK_NAME)) {
                    collectFeesForFailedPaymentsTaskHandler.collectAndPersistFees(paymentTaskMessage.getPaymentExternalId());
                } else {
                    LOGGER.error("Task [{}] is not supported", paymentTaskMessage.getTask());
                }

                taskQueue.markMessageAsProcessed(paymentTaskMessage.getQueueMessage());
            } catch (Exception e) {
                LOGGER.error(format("Error processing payment task from SQS message [queueMessageId=%s] [errorMessage=%s]",
                                paymentTaskMessage.getQueueMessageId(),
                                e.getMessage()),
                        kv(PAYMENT_EXTERNAL_ID, paymentTaskMessage.getPaymentExternalId())
                );
            }
        }
    }
}
