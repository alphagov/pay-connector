package uk.gov.pay.connector.card.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.ChargesAwaitingCaptureMetricEmitter;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.pay.connector.queue.capture.ChargeCaptureMessage;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardCaptureProcessTest {

    private static final String chargeExternalId = "some-charge-id";
    @Mock
    CaptureQueue captureQueue;
    @Mock
    CardCaptureService cardCaptureService;
    @Mock
    CaptureResponse captureResponse;
    @Mock
    ChargeCaptureMessage chargeCaptureMessage;
    @Mock
    ChargeService chargeService;
    @Mock
    ChargesAwaitingCaptureMetricEmitter chargesAwaitingCaptureMetricEmitter;
    CardCaptureProcess cardCaptureProcess;

    @BeforeEach
    void setUp() throws Exception {
        List<ChargeCaptureMessage> messages = Arrays.asList(chargeCaptureMessage);

        when(chargeCaptureMessage.getChargeId()).thenReturn(chargeExternalId);
        when(captureQueue.retrieveChargesForCapture()).thenReturn(messages);
        when(cardCaptureService.doCapture(anyString())).thenReturn(captureResponse);

        cardCaptureProcess = new CardCaptureProcess(captureQueue, cardCaptureService,
                chargeService, chargesAwaitingCaptureMetricEmitter);
    }

    @Test
    void shouldMarkMessageAsProcessedGivenSuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(true);

        cardCaptureProcess.handleCaptureMessages();

        verify(captureQueue).markMessageAsProcessed(chargeCaptureMessage.getQueueMessage());
    }

    @Test
    void shouldScheduleRetriableMessageGivenUnsuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(false);
        when(chargeService.isChargeRetriable(chargeExternalId)).thenReturn(true);

        cardCaptureProcess.handleCaptureMessages();

        verify(captureQueue).scheduleMessageForRetry(chargeCaptureMessage.getQueueMessage());
    }

    @Test
    void shouldMarkNonRetribaleMessageAsProcessed_MarkChargeAsCaptureErrorGivenUnsuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(false);
        when(chargeService.isChargeRetriable(chargeExternalId)).thenReturn(false);

        cardCaptureProcess.handleCaptureMessages();

        verify(cardCaptureService).markChargeAsCaptureError(chargeExternalId);
        verify(captureQueue).markMessageAsProcessed(chargeCaptureMessage.getQueueMessage());
    }

    @Test
    void shouldMarkMessageAsProcessedGivenChargeInCapturedState() throws QueueException {
        when(cardCaptureService.doCapture(anyString())).thenThrow(IllegalStateRuntimeException.class);
        when(chargeService.isChargeCaptureSuccess(anyString())).thenReturn(true);

        cardCaptureProcess.handleCaptureMessages();

        verify(captureQueue).markMessageAsProcessed(chargeCaptureMessage.getQueueMessage());
    }
}
