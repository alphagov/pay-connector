package uk.gov.pay.connector.paymentprocessor.service;

import com.amazonaws.auth.policy.resources.SQSQueueResource;
import uk.gov.pay.connector.queue.QueueMessage;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

// @TODO(sfount) replace `CardCaptureProcess` when feature flag is switched
public class CardCaptureMessageProcess {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CardCaptureMessageProcess.class);
   
    @Inject
    public CardCaptureMessageProcess() { 
    }
    
    public void runCapture(QueueMessage captureMessage) {  
        LOGGER.info("SQS message received [{}] - {}", captureMessage.getMessageId(), captureMessage.getMessageBody());
    }
}
