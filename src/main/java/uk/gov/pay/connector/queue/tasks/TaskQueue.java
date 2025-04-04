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
import uk.gov.pay.connector.queue.tasks.model.Task;

import jakarta.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.kv;

public class TaskQueue extends AbstractQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskQueue.class);
    private final int defaultDeliveryDelayInSeconds;

    @Inject
    public TaskQueue(SqsQueueService sqsQueueService,
                     ConnectorConfiguration connectorConfiguration,
                     ObjectMapper objectMapper) {
        super(sqsQueueService, objectMapper,
                connectorConfiguration.getSqsConfig().getTaskQueueUrl(),
                connectorConfiguration.getTaskQueueConfig().getFailedMessageRetryDelayInSeconds());
        this.defaultDeliveryDelayInSeconds = connectorConfiguration.getTaskQueueConfig().getDeliveryDelayInSeconds();
    }

    public void addTaskToQueue(Task task) throws QueueException, JsonProcessingException {
        addTaskToQueue(task, this.defaultDeliveryDelayInSeconds);
    }

    public void addTaskToQueue(Task task, int deliveryDelayInSeconds) throws QueueException, JsonProcessingException {
        String message = objectMapper.writeValueAsString(task);
        QueueMessage queueMessage = sendMessageToQueueWithDelay(message, deliveryDelayInSeconds);
        LOGGER.info("Task added to queue",
                kv("task_type", task.getTaskType().getName()),
                kv("message_id", queueMessage.getMessageId()));
    }

    public List<TaskMessage> retrieveTaskQueueMessages() throws QueueException {
        List<QueueMessage> queueMessages = retrieveMessages();

        return queueMessages
                .stream()
                .map(this::deserializeMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private TaskMessage deserializeMessage(QueueMessage qm) {
        try {
            Task task = objectMapper.readValue(qm.getMessageBody(), Task.class);
            return TaskMessage.of(task, qm);
        } catch (IOException e) {
            LOGGER.error("Error parsing message from tasks queue",
                    kv("message", qm.getMessageBody()),
                    kv("error", e.getMessage()));
            return null;
        }
    }
}
