package uk.gov.pay.connector.paymentprocessor.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.queue.CaptureQueue;
import uk.gov.pay.connector.queue.ChargeCaptureMessage;
import uk.gov.pay.connector.queue.QueueException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureMessageProcessTest {

    private static final int RETRIABLE_NUMBER_OF_CAPTURE_ATTEMPTS = 1;
    private static final int MAXIMUM_NUMBER_OF_CAPTURE_ATTEMPTS = 10;

    @Mock
    CaptureQueue captureQueue;

    @Mock
    CardCaptureService cardCaptureService;

    @Mock
    ConnectorConfiguration connectorConfiguration;

    @Mock
    ChargeDao chargeDao;

    @Mock
    CaptureResponse captureResponse;

    @Mock
    ChargeCaptureMessage chargeCaptureMessage;

    private static final Long chargeId = 1L;
    private static final String chargeExternalId = "some-charge-id";

    CardCaptureMessageProcess cardCaptureMessageProcess;

    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);

    @Before
    public void setUp() throws Exception {
        List<ChargeCaptureMessage> messages = Arrays.asList(chargeCaptureMessage);
        CaptureProcessConfig captureProcessConfig = mock(CaptureProcessConfig.class);
        ChargeEntity chargeEntity = mock(ChargeEntity.class);

        Logger root = (Logger) LoggerFactory.getLogger(CardCaptureMessageProcess.class);
        root.setLevel(Level.WARN);
        root.addAppender(mockAppender);

        when(chargeCaptureMessage.getChargeId()).thenReturn(chargeExternalId);
        when(captureQueue.retrieveChargesForCapture()).thenReturn(messages);
        when(captureProcessConfig.getCaptureUsingSQS()).thenReturn(true);
        when(captureProcessConfig.getMaximumRetries()).thenReturn(MAXIMUM_NUMBER_OF_CAPTURE_ATTEMPTS);
        when(connectorConfiguration.getCaptureProcessConfig()).thenReturn(captureProcessConfig);
        when(cardCaptureService.doCapture(anyString())).thenReturn(captureResponse);
        when(chargeEntity.getId()).thenReturn(chargeId);
        when(chargeDao.findByExternalId(chargeExternalId)).thenReturn(Optional.of(chargeEntity));

        cardCaptureMessageProcess = new CardCaptureMessageProcess(captureQueue, cardCaptureService, connectorConfiguration, chargeDao);
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
        when(chargeDao.countCaptureRetriesForCharge(chargeId)).thenReturn(RETRIABLE_NUMBER_OF_CAPTURE_ATTEMPTS);

        cardCaptureMessageProcess.handleCaptureMessages();

        verify(captureQueue).scheduleMessageForRetry(chargeCaptureMessage);
    }

    @Test
    public void shouldMarkNonRetribaleMessageAsProcessed_MarkChargeAsCaptureErrorGivenUnsuccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(false);
        when(chargeDao.countCaptureRetriesForCharge(chargeId)).thenReturn(MAXIMUM_NUMBER_OF_CAPTURE_ATTEMPTS);

        cardCaptureMessageProcess.handleCaptureMessages();

        verify(cardCaptureService).markChargeAsCaptureError(chargeExternalId);
        verify(captureQueue).markMessageAsProcessed(chargeCaptureMessage);
    }

    @Test
    public void shouldThrowQueueExceptionGivenNonExistingCharge_UneccessfulChargeCapture() throws QueueException {
        when(captureResponse.isSuccessful()).thenReturn(false);
        when(chargeDao.findByExternalId(chargeExternalId)).thenReturn(Optional.empty());

        cardCaptureMessageProcess.handleCaptureMessages();

        verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Error capturing charge from SQS message [Invalid message on capture retry " + chargeExternalId + "]")), is(true));
    }
}
