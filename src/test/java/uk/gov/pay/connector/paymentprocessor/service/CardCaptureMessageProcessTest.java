package uk.gov.pay.connector.paymentprocessor.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.queue.CaptureQueue;
import uk.gov.pay.connector.queue.ChargeCaptureMessage;
import uk.gov.pay.connector.queue.QueueException;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureMessageProcessTest {
    
    @Mock
    CaptureQueue captureQueue;

    @Mock
    CardCaptureService cardCaptureService;

    @Mock
    ConnectorConfiguration connectorConfiguration;

    @Mock
    CaptureResponse captureResponse;

    @Mock
    ChargeCaptureMessage chargeCaptureMessage;

    @Mock
    ChargeService chargeService;

    private static final String chargeExternalId = "some-charge-id";

    CardCaptureMessageProcess cardCaptureMessageProcess;

    @Before
    public void setUp() throws Exception {
        List<ChargeCaptureMessage> messages = Arrays.asList(chargeCaptureMessage);
        CaptureProcessConfig captureProcessConfig = mock(CaptureProcessConfig.class);

        when(chargeCaptureMessage.getChargeId()).thenReturn(chargeExternalId);
        when(captureQueue.retrieveChargesForCapture()).thenReturn(messages);
        when(captureProcessConfig.getCaptureUsingSQS()).thenReturn(true);
        when(connectorConfiguration.getCaptureProcessConfig()).thenReturn(captureProcessConfig);
        when(cardCaptureService.doCapture(anyString())).thenReturn(captureResponse);

        cardCaptureMessageProcess = new CardCaptureMessageProcess(captureQueue, cardCaptureService, connectorConfiguration, chargeService);
    }

    @Test
    public void shouldMarkMessageAsProcessedGivenSuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(true);

        cardCaptureMessageProcess.handleCaptureMessages();

        verify(captureQueue).markMessageAsProcessed(chargeCaptureMessage);
    }

    @Test
    public void shouldScheduleRetriableMessageGivenUnsuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(false);
        when(chargeService.isChargeRetriable(chargeExternalId)).thenReturn(true);

        cardCaptureMessageProcess.handleCaptureMessages();

        verify(captureQueue).scheduleMessageForRetry(chargeCaptureMessage);
    }

    @Test
    public void shouldMarkNonRetribaleMessageAsProcessed_MarkChargeAsCaptureErrorGivenUnsuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(false);
        when(chargeService.isChargeRetriable(chargeExternalId)).thenReturn(false);

        cardCaptureMessageProcess.handleCaptureMessages();

        verify(cardCaptureService).markChargeAsCaptureError(chargeExternalId);
        verify(captureQueue).markMessageAsProcessed(chargeCaptureMessage);
    }

    @Test
    public void shouldMarkMessageAsProcessedGivenChargeInCapturedState() throws QueueException {
        when(cardCaptureService.doCapture(anyString())).thenThrow(IllegalStateRuntimeException.class);
        when(chargeService.isChargeCaptured(anyString())).thenReturn(true);

        cardCaptureMessageProcess.handleCaptureMessages();

        verify(captureQueue).markMessageAsProcessed(chargeCaptureMessage);
    }
}
