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
import uk.gov.pay.connector.queue.CaptureQueue;
import uk.gov.pay.connector.queue.ChargeCaptureMessage;
import uk.gov.pay.connector.queue.QueueException;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureProcessTest {

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

    private static final String chargeExternalId = "some-charge-id";

    CardCaptureProcess cardCaptureProcess;

    @Before
    public void setUp() throws Exception {
        List<ChargeCaptureMessage> messages = Arrays.asList(chargeCaptureMessage);

        when(chargeCaptureMessage.getChargeId()).thenReturn(chargeExternalId);
        when(captureQueue.retrieveChargesForCapture()).thenReturn(messages);
        when(cardCaptureService.doCapture(anyString())).thenReturn(captureResponse);

        cardCaptureProcess = new CardCaptureProcess(captureQueue, cardCaptureService,
                chargeService, chargesAwaitingCaptureMetricEmitter);
    }

    @Test
    public void shouldMarkMessageAsProcessedGivenSuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(true);

        cardCaptureProcess.handleCaptureMessages();

        verify(captureQueue).markMessageAsProcessed(chargeCaptureMessage);
    }

    @Test
    public void shouldScheduleRetriableMessageGivenUnsuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(false);
        when(chargeService.isChargeRetriable(chargeExternalId)).thenReturn(true);

        cardCaptureProcess.handleCaptureMessages();

        verify(captureQueue).scheduleMessageForRetry(chargeCaptureMessage);
    }

    @Test
    public void shouldMarkNonRetribaleMessageAsProcessed_MarkChargeAsCaptureErrorGivenUnsuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(false);
        when(chargeService.isChargeRetriable(chargeExternalId)).thenReturn(false);

        cardCaptureProcess.handleCaptureMessages();

        verify(cardCaptureService).markChargeAsCaptureError(chargeExternalId);
        verify(captureQueue).markMessageAsProcessed(chargeCaptureMessage);
    }

    @Test
    public void shouldMarkMessageAsProcessedGivenChargeInCapturedState() throws QueueException {
        when(cardCaptureService.doCapture(anyString())).thenThrow(IllegalStateRuntimeException.class);
        when(chargeService.isChargeCaptureSuccess(anyString())).thenReturn(true);

        cardCaptureProcess.handleCaptureMessages();

        verify(captureQueue).markMessageAsProcessed(chargeCaptureMessage);
    }
}
