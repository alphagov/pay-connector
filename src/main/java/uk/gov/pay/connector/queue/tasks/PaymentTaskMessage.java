package uk.gov.pay.connector.queue.tasks;

import uk.gov.service.payments.commons.queue.model.QueueMessage;

public final class PaymentTaskMessage {
    private PaymentTask paymentTask;
    private QueueMessage queueMessage;

    private PaymentTaskMessage(PaymentTask paymentTask, QueueMessage queueMessage) {
        this.paymentTask = paymentTask;
        this.queueMessage = queueMessage;
    }

    public static PaymentTaskMessage of(PaymentTask paymentTask, QueueMessage queueMessage) {
        return new PaymentTaskMessage(paymentTask, queueMessage);
    }

    public String getPaymentExternalId() {
        return paymentTask.getPaymentExternalId();
    }

    public String getTask() {
        return paymentTask.getTask();
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
