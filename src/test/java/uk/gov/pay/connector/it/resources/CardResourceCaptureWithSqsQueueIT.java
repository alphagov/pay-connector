package uk.gov.pay.connector.it.resources;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.Instant;
import java.util.List;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;

public class CardResourceCaptureWithSqsQueueIT {
    @RegisterExtension
    public static ITestBaseExtension app = new ITestBaseExtension(
            "sandbox",
            io.dropwizard.testing.ConfigOverride.config("captureProcessConfig.backgroundProcessingEnabled", "false"));

    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
    private String captureApproveUrl;
    
    @BeforeEach
    void setUpLogger() {
        Logger root = (Logger) LoggerFactory.getLogger(CaptureQueue.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    void shouldAddChargeToQueueAndSubmitForCapture_IfChargeWasPreviouslyAuthorised() {
        String chargeId = app.authoriseNewCharge();
        app.givenSetup()
                .post(app.captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        app.assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
        app.assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());

        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();

        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Charge [" + chargeId + "] added to capture queue. Message ID [")), is(true));
    }

    @Test
    void shouldAddChargeToQueueAndSubmitForCapture_IfChargeWasAwaitingCapture() {
        String chargeId = app.addCharge(AWAITING_CAPTURE_REQUEST, "ref", Instant.now().minus(48, HOURS).plus(1, MINUTES), RandomIdGenerator.newId());

        captureApproveUrl = app.captureUrlForAwaitingCaptureCharge(app.getAccountId(), chargeId);

        app.givenSetup()
                .post(captureApproveUrl)
                .then()
                .statusCode(204);

        app.assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
        app.assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());

        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();

        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Charge [" + chargeId + "] added to capture queue. Message ID [")), is(true));
    }
}
