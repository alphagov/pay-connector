package uk.gov.pay.connector.queue.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.model.QueueMessage;
import uk.gov.service.payments.commons.queue.sqs.AbstractQueue;
import uk.gov.service.payments.commons.queue.sqs.SqsQueueService;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TaskQueue extends AbstractQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskQueue.class);

    @Inject
    public TaskQueue(SqsQueueService sqsQueueService,
                     ConnectorConfiguration connectorConfiguration,
                     ObjectMapper objectMapper) {
        super(sqsQueueService, objectMapper,
                connectorConfiguration.getSqsConfig().getTaskQueueUrl(),
                connectorConfiguration.getTaskQueueConfig().getFailedMessageRetryDelayInSeconds());
    }

    public void addTaskToQueue(PaymentTask paymentTask) throws QueueException, JsonProcessingException {
        String message = objectMapper.writeValueAsString(paymentTask);
        QueueMessage queueMessage = sendMessageToQueue(message);
        LOGGER.info("Task [{}] added to queue. Message ID [{}]",
                paymentTask.getPaymentExternalId(), queueMessage.getMessageId());
    }

    public List<PaymentTaskMessage> retrieveTaskQueueMessages() throws QueueException {
        List<QueueMessage> queueMessages = retrieveMessages();

        return queueMessages
                .stream()
                .map(this::deserializeMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private PaymentTaskMessage deserializeMessage(QueueMessage qm) {
        try {
            PaymentTask paymentTask = objectMapper.readValue(qm.getMessageBody(), PaymentTask.class);

            return PaymentTaskMessage.of(paymentTask, qm);
        } catch (IOException e) {
            LOGGER.error("Error parsing message [message={}] from tasks queue [error={}]",
                    qm.getMessageBody(), e.getMessage());
            return null;
        }
    }
}
