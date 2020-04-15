package uk.gov.pay.connector.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;

import java.util.List;

public abstract class AbstractQueue {

    private static final String MESSAGE_ATTRIBUTES_TO_RECEIVE = "All";
    protected ObjectMapper objectMapper;
    private String queueUrl;
    private int failedMessageRetryDelayInSeconds;
    private SqsQueueService sqsQueueService;

    public AbstractQueue(SqsQueueService sqsQueueService, ObjectMapper objectMapper,
                         String queueUrl, int failedMessageRetryDelayInSeconds) {
        this.sqsQueueService = sqsQueueService;
        this.queueUrl = queueUrl;
        this.failedMessageRetryDelayInSeconds = failedMessageRetryDelayInSeconds;
        this.objectMapper = objectMapper;
    }

    public QueueMessage sendMessageToQueue(String message) throws QueueException {
        return sqsQueueService.sendMessage(queueUrl, message);
    }

    public List<QueueMessage> retrieveMessages() throws QueueException {
        return sqsQueueService
                .receiveMessages(this.queueUrl, MESSAGE_ATTRIBUTES_TO_RECEIVE);
    }

    public void markMessageAsProcessed(QueueMessage queueMessage) throws QueueException {
        sqsQueueService.deleteMessage(this.queueUrl, queueMessage.getReceiptHandle());
    }

    public void scheduleMessageForRetry(QueueMessage queueMessage) throws QueueException {
        sqsQueueService.deferMessage(this.queueUrl, queueMessage.getReceiptHandle(), failedMessageRetryDelayInSeconds);
    }
}

