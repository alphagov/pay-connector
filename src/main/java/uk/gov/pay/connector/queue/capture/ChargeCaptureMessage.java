package uk.gov.pay.connector.queue.capture;

import uk.gov.service.payments.commons.queue.model.QueueMessage;

public class ChargeCaptureMessage {
    private CaptureCharge captureCharge;
    private QueueMessage queueMessage;

    private ChargeCaptureMessage(CaptureCharge captureCharge, QueueMessage queueMessage) {
        this.captureCharge = captureCharge;
        this.queueMessage = queueMessage;
    }

    public static ChargeCaptureMessage of(CaptureCharge captureCharge, QueueMessage queueMessage) {
        return new ChargeCaptureMessage(captureCharge, queueMessage);
    }

    public String getChargeId() {
        return captureCharge.getChargeId();
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
