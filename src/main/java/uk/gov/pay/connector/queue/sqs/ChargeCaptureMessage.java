package uk.gov.pay.connector.queue.sqs;

import uk.gov.pay.connector.queue.QueueMessage;

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

    public String getReceiptHandle() {
        return queueMessage.getReceiptHandle();
    }
}
