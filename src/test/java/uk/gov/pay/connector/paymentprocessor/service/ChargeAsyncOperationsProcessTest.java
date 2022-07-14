package uk.gov.pay.connector.paymentprocessor.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.ChargesAwaitingCaptureMetricEmitter;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.queue.capture.ChargeAsyncOperationsQueue;
import uk.gov.pay.connector.queue.capture.ChargeAsyncOperationsMessage;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ChargeAsyncOperationsProcessTest {

    private static final String chargeExternalId = "some-charge-id";
    @Mock
    ChargeAsyncOperationsQueue chargeAsyncOperationsQueue;
    @Mock
    CardCaptureService cardCaptureService;
    @Mock
    CaptureResponse captureResponse;
    @Mock
    ChargeAsyncOperationsMessage chargeAsyncOperationsMessage;
    @Mock
    ChargeService chargeService;
    @Mock
    ChargesAwaitingCaptureMetricEmitter chargesAwaitingCaptureMetricEmitter;
    ChargeAsyncOperationsProcess chargeAsyncOperationsProcess;

    @Before
    public void setUp() throws Exception {
        List<ChargeAsyncOperationsMessage> messages = Arrays.asList(chargeAsyncOperationsMessage);

        when(chargeAsyncOperationsMessage.getChargeId()).thenReturn(chargeExternalId);
        when(chargeAsyncOperationsQueue.retrieveAsyncOperations()).thenReturn(messages);
        when(cardCaptureService.doCapture(anyString())).thenReturn(captureResponse);

        chargeAsyncOperationsProcess = new ChargeAsyncOperationsProcess(chargeAsyncOperationsQueue, cardCaptureService,
                chargeService, chargesAwaitingCaptureMetricEmitter);
    }

    @Test
    public void shouldMarkMessageAsProcessedGivenSuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(true);

        chargeAsyncOperationsProcess.handleChargeAsyncOperationsMessage();

        verify(chargeAsyncOperationsQueue).markMessageAsProcessed(chargeAsyncOperationsMessage.getQueueMessage());
    }

    @Test
    public void shouldScheduleRetriableMessageGivenUnsuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(false);
        when(chargeService.isChargeCaptureRetriable(chargeExternalId)).thenReturn(true);

        chargeAsyncOperationsProcess.handleChargeAsyncOperationsMessage();

        verify(chargeAsyncOperationsQueue).scheduleMessageForRetry(chargeAsyncOperationsMessage.getQueueMessage());
    }

    @Test
    public void shouldMarkNonRetribaleMessageAsProcessed_MarkChargeAsCaptureErrorGivenUnsuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(false);
        when(chargeService.isChargeCaptureRetriable(chargeExternalId)).thenReturn(false);

        chargeAsyncOperationsProcess.handleChargeAsyncOperationsMessage();

        verify(cardCaptureService).markChargeAsCaptureError(chargeExternalId);
        verify(chargeAsyncOperationsQueue).markMessageAsProcessed(chargeAsyncOperationsMessage.getQueueMessage());
    }

    @Test
    public void shouldMarkMessageAsProcessedGivenChargeInCapturedState() throws QueueException {
        when(cardCaptureService.doCapture(anyString())).thenThrow(IllegalStateRuntimeException.class);
        when(chargeService.isChargeCaptureSuccess(anyString())).thenReturn(true);

        chargeAsyncOperationsProcess.handleChargeAsyncOperationsMessage();

        verify(chargeAsyncOperationsQueue).markMessageAsProcessed(chargeAsyncOperationsMessage.getQueueMessage());
    }
}
