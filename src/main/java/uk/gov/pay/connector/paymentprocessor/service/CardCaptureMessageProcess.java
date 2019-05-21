package uk.gov.pay.connector.paymentprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.queue.CaptureQueue;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.QueueMessage;

import javax.inject.Inject;
import java.util.List;

// @TODO(sfount) replace `CardCaptureProcess` when feature flag is switched
public class CardCaptureMessageProcess {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CardCaptureMessageProcess.class);
    private final CaptureQueue captureQueue;

    @Inject
    public CardCaptureMessageProcess(CaptureQueue captureQueue) { 
        this.captureQueue = captureQueue;
    }
    
    public void handleCaptureMessages() throws QueueException { 
        List<QueueMessage> captureMessages = captureQueue.receiveCaptureMessages();     
        for (QueueMessage message: captureMessages) {
            try {
                runCapture(message);
                
                // @TODO(sfount) model charge message as class, include charge ID (extracted) and message receipt handle
                captureQueue.markMessageAsProcessed(message);
            } catch (Exception e) {
                LOGGER.warn("Error capturing charge from SQS message [{}]", e);
            }
        }
    }
    
    private void runCapture(QueueMessage captureMessage) {  
        LOGGER.info("SQS message received [{}] - {}", captureMessage.getMessageId(), captureMessage.getMessageBody());
    }
}
