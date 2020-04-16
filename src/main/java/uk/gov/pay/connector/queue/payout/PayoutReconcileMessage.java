package uk.gov.pay.connector.queue.payout;

import uk.gov.pay.connector.queue.QueueMessage;

public class PayoutReconcileMessage {
    private Payout payout;
    private QueueMessage queueMessage;

    private PayoutReconcileMessage(Payout payout, QueueMessage queueMessage) {
        this.payout = payout;
        this.queueMessage = queueMessage;
    }

    public static PayoutReconcileMessage of(Payout payout, QueueMessage queueMessage) {
        return new PayoutReconcileMessage(payout, queueMessage);
    }

    public String getGatewayPayoutId() {
        return payout.getGatewayPayoutId();
    }

    public String getConnectAccountId() {
        return payout.getConnectAccountId();
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
