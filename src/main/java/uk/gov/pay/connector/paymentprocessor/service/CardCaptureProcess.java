package uk.gov.pay.connector.paymentprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.ChargesAwaitingCaptureMetricEmitter;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.queue.CaptureQueue;
import uk.gov.pay.connector.queue.ChargeCaptureMessage;
import uk.gov.pay.connector.queue.QueueException;

import javax.inject.Inject;
import java.util.List;

public class CardCaptureProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardCaptureProcess.class);
    private final CaptureQueue captureQueue;
    private final ChargeService chargeService;
    private CardCaptureService cardCaptureService;

    @Inject
    public CardCaptureProcess(CaptureQueue captureQueue,
                              CardCaptureService cardCaptureService,
                              ChargeService chargeService,
                              ChargesAwaitingCaptureMetricEmitter chargesAwaitingCaptureMetricEmitter) {
        this.captureQueue = captureQueue;
        this.cardCaptureService = cardCaptureService;
        this.chargeService = chargeService;

        chargesAwaitingCaptureMetricEmitter.register();
    }

    public void handleCaptureMessages() throws QueueException {
        List<ChargeCaptureMessage> captureMessages = captureQueue.retrieveChargesForCapture();
        for (ChargeCaptureMessage message : captureMessages) {
            try {
                LOGGER.info("Charge capture message received - [externalChargeId={}] [queueMessageId={}] [queueMessageReceiptHandle={}]",
                        message.getChargeId(),
                        message.getQueueMessageId(),
                        message.getQueueMessageReceiptHandle()
                );

                runCapture(message);
            } catch (Exception e) {
                LOGGER.warn("Error capturing charge from SQS message [externalChargeId={}] [queueMessageId={}] [errorMessage={}]",
                        message.getChargeId(),
                        message.getQueueMessageId(),
                        e.getMessage()
                );
            }
        }
    }

    private void runCapture(ChargeCaptureMessage captureMessage) throws QueueException {
        String externalChargeId = captureMessage.getChargeId();

        try {
            CaptureResponse gatewayResponse = cardCaptureService.doCapture(externalChargeId);

            if (gatewayResponse.isSuccessful()) {
                captureQueue.markMessageAsProcessed(captureMessage);
            } else {
                LOGGER.info(
                        "Failed to capture [externalChargeId={}] due to: {}",
                        externalChargeId,
                        gatewayResponse.getErrorMessage()
                );
                handleCaptureRetry(captureMessage);
            }
        } catch (IllegalStateRuntimeException e) {
            handleCapturedInvalidTransition(captureMessage, e);
        }
    }

    private void handleCaptureRetry(ChargeCaptureMessage captureMessage) throws QueueException {
        boolean shouldRetry = chargeService.isChargeRetriable(captureMessage.getChargeId());

        if (shouldRetry) {
            LOGGER.info("Charge capture message [{}] scheduled for retry.", captureMessage.getChargeId());
            captureQueue.scheduleMessageForRetry(captureMessage);
        } else {
            cardCaptureService.markChargeAsCaptureError(captureMessage.getChargeId());
            captureQueue.markMessageAsProcessed(captureMessage);
        }
    }

    private void handleCapturedInvalidTransition(ChargeCaptureMessage captureMessage, IllegalStateRuntimeException e) throws QueueException {
        if (chargeService.isChargeCaptureSuccess(captureMessage.getChargeId())) {
            LOGGER.info(
                    "Charge capture message [{}] already captured - marking as processed. [chargeId={}]",
                    captureMessage.getQueueMessageId(),
                    captureMessage.getChargeId());
            captureQueue.markMessageAsProcessed(captureMessage);
            return;
        }

        LOGGER.info(
                "Capture process non-success illegal state transition for message {} [chargeId={}]",
                captureMessage.getQueueMessageId(),
                captureMessage.getChargeId());
        throw e;
    }
}
