package uk.gov.pay.connector.queue.tasks;

import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.service.payments.commons.queue.model.QueueMessage;

public final class TaskMessage {
    private final Task task;
    private final QueueMessage queueMessage;

    private TaskMessage(Task task, QueueMessage queueMessage) {
        this.task = task;
        this.queueMessage = queueMessage;
    }

    public static TaskMessage of(Task task, QueueMessage queueMessage) {
        return new TaskMessage(task, queueMessage);
    }

    public Task getTask() {
        return task;
    }

    public String getQueueMessageReceiptHandle() {
        return queueMessage.getReceiptHandle();
    }

    public Object getQueueMessageId() {
        return queueMessage.getMessageId();
    }

    public QueueMessage getQueueMessage() {
        return queueMessage;
    }
}
