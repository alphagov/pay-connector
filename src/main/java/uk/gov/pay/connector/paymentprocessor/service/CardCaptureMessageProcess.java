package uk.gov.pay.connector.paymentprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.queue.QueueMessage;

import javax.inject.Inject;

// @TODO(sfount) replace `CardCaptureProcess` when feature flag is switched
public class CardCaptureMessageProcess {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CardCaptureMessageProcess.class);
    
    private CardCaptureService cardCaptureService;
   
    @Inject
    public CardCaptureMessageProcess(CardCaptureService cardCaptureService) { 
        this.cardCaptureService = cardCaptureService;
    }
    
    public CaptureResponse runCapture(QueueMessage captureMessage) {  
        LOGGER.info("SQS message received [{}] - {}", captureMessage.getMessageId(), captureMessage.getMessageBody());
        String chargeExternalId = captureMessage.getMessageBody();
        return cardCaptureService.doCapture(chargeExternalId);
    }
}
