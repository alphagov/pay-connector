package uk.gov.pay.connector.paymentprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.queue.CaptureQueue;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.ChargeCaptureMessage;

import javax.inject.Inject;
import java.util.List;

// @TODO(sfount) replace `CardCaptureProcess` when feature flag is switched
public class CardCaptureMessageProcess {
  
    private static final Logger LOGGER = LoggerFactory.getLogger(CardCaptureMessageProcess.class);
    private final CaptureQueue captureQueue;
    private final Boolean captureUsingSqs;
    private CardCaptureService cardCaptureService;

    @Inject
    public CardCaptureMessageProcess(CaptureQueue captureQueue, CardCaptureService cardCaptureService, ConnectorConfiguration connectorConfiguration) { 
        this.captureQueue = captureQueue;
        this.cardCaptureService = cardCaptureService;
        this.captureUsingSqs = connectorConfiguration.getCaptureProcessConfig().getCaptureUsingSQS();
    }
    
    public void handleCaptureMessages() throws QueueException { 
        List<ChargeCaptureMessage> captureMessages = captureQueue.retrieveChargesForCapture();    
        for (ChargeCaptureMessage message: captureMessages) {
            try {
                LOGGER.info("Charge capture message received - {}", message.getChargeId());
                
                if (captureUsingSqs) {
                    runCapture(message);
                } else {
                    LOGGER.info("Charge capture not enabled for message capture request - {}", message.getChargeId());
                }
            } catch (Exception e) {
                LOGGER.warn("Error capturing charge from SQS message [{}]", e.getMessage());
            }
        }
    }
    
    private void runCapture(ChargeCaptureMessage captureMessage) throws QueueException {
        String externalChargeId = captureMessage.getChargeId();

        CaptureResponse gatewayResponse = cardCaptureService.doCapture(externalChargeId);

        // @TODO(sfount) handling gateway response failure should be considered in PP-5171
        if (gatewayResponse.isSuccessful()) {
            captureQueue.markMessageAsProcessed(captureMessage);
        } else {
            LOGGER.info(
                    "Failed to capture [externalChargeId={}] due to: {}",
                    externalChargeId,
                    gatewayResponse.getError().get().getMessage()
            );
        }
    }
    
}
