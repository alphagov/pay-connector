package uk.gov.pay.connector.queue.capture;

import uk.gov.service.payments.commons.queue.model.QueueMessage;

public class ChargeAsyncOperationsMessage {
    private final AsyncChargeOperation asyncChargeOperation;
    private final QueueMessage queueMessage;

    private ChargeAsyncOperationsMessage(AsyncChargeOperation asyncChargeOperation, QueueMessage queueMessage) {
        this.asyncChargeOperation = asyncChargeOperation;
        this.queueMessage = queueMessage;
    }

    public static ChargeAsyncOperationsMessage of(AsyncChargeOperation asyncChargeOperation, QueueMessage queueMessage) {
        return new ChargeAsyncOperationsMessage(asyncChargeOperation, queueMessage);
    }

    public String getChargeId() {
        return asyncChargeOperation.getChargeId();
    }

    public AsyncChargeOperationKey getOperationKey() {
        return asyncChargeOperation.getOperationKey();
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
