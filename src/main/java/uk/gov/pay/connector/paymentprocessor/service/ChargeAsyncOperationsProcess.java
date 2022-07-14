package uk.gov.pay.connector.paymentprocessor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.charge.ChargesAwaitingCaptureMetricEmitter;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.queue.capture.ChargeAsyncOperationsQueue;
import uk.gov.pay.connector.queue.capture.ChargeAsyncOperationsMessage;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import javax.inject.Inject;
import java.util.List;

import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class ChargeAsyncOperationsProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChargeAsyncOperationsProcess.class);
    private final ChargeAsyncOperationsQueue chargeAsyncOperationsQueue;
    private final ChargeService chargeService;
    private CardCaptureService cardCaptureService;

    @Inject
    public ChargeAsyncOperationsProcess(ChargeAsyncOperationsQueue chargeAsyncOperationsQueue,
                                        CardCaptureService cardCaptureService,
                                        ChargeService chargeService,
                                        ChargesAwaitingCaptureMetricEmitter chargesAwaitingCaptureMetricEmitter) {
        this.chargeAsyncOperationsQueue = chargeAsyncOperationsQueue;
        this.cardCaptureService = cardCaptureService;
        this.chargeService = chargeService;

        chargesAwaitingCaptureMetricEmitter.register();
    }

    public void handleChargeAsyncOperationsMessage() throws QueueException {
        List<ChargeAsyncOperationsMessage> chargeAsyncOperationsMessages = chargeAsyncOperationsQueue.retrieveAsyncOperations();
        for (ChargeAsyncOperationsMessage message : chargeAsyncOperationsMessages) {
            try {
                MDC.put(PAYMENT_EXTERNAL_ID, message.getChargeId());
                LOGGER.info("Charge async operation message received [operationKey={}] - [queueMessageId={}] [queueMessageReceiptHandle={}]",
                        message.getOperationKey(),
                        message.getQueueMessageId(),
                        message.getQueueMessageReceiptHandle()
                );

                // for now the only and default operation on charges is to capture
                runCapture(message);
            } catch (Exception e) {
                LOGGER.warn("Error processing async charge operation from SQS message [queueMessageId={}] [errorMessage={}]",
                        message.getQueueMessageId(),
                        e.getMessage()
                );
            } finally {
                MDC.remove(PAYMENT_EXTERNAL_ID);
            }
        }
    }

    private void runCapture(ChargeAsyncOperationsMessage captureMessage) throws QueueException {
        String externalChargeId = captureMessage.getChargeId();

        try {
            CaptureResponse gatewayResponse = cardCaptureService.doCapture(externalChargeId);

            if (gatewayResponse.isSuccessful()) {
                chargeAsyncOperationsQueue.markMessageAsProcessed(captureMessage.getQueueMessage());
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

    private void handleCaptureRetry(ChargeAsyncOperationsMessage captureMessage) throws QueueException {
        boolean shouldRetry = chargeService.isChargeCaptureRetriable(captureMessage.getChargeId());

        if (shouldRetry) {
            LOGGER.info("Charge capture message [{}] scheduled for retry.", captureMessage.getChargeId());
            chargeAsyncOperationsQueue.scheduleMessageForRetry(captureMessage.getQueueMessage());
        } else {
            cardCaptureService.markChargeAsCaptureError(captureMessage.getChargeId());
            chargeAsyncOperationsQueue.markMessageAsProcessed(captureMessage.getQueueMessage());
        }
    }

    private void handleCapturedInvalidTransition(ChargeAsyncOperationsMessage captureMessage, IllegalStateRuntimeException e) throws QueueException {
        if (chargeService.isChargeCaptureSuccess(captureMessage.getChargeId())) {
            LOGGER.info(
                    "Charge capture message [{}] already captured - marking as processed. [chargeId={}]",
                    captureMessage.getQueueMessageId(),
                    captureMessage.getChargeId());
            chargeAsyncOperationsQueue.markMessageAsProcessed(captureMessage.getQueueMessage());
            return;
        }

        LOGGER.info(
                "Capture process non-success illegal state transition for message {} [chargeId={}]",
                captureMessage.getQueueMessageId(),
                captureMessage.getChargeId());
        throw e;
    }
}
