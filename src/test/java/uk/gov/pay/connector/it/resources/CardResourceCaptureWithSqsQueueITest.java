package uk.gov.pay.connector.it.resources;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.queue.CaptureQueue;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.ZonedDateTime;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml",
        configOverrides = {
                @ConfigOverride(key = "captureProcessConfig.captureUsingSQS", value = "true"),
                @ConfigOverride(key = "backgroundProcessingEnabled", value = "false")
        },
        withDockerSQS = true
)
public class CardResourceCaptureWithSqsQueueITest extends ChargingITestBase {

    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
    private String captureApproveUrl;

    public CardResourceCaptureWithSqsQueueITest() {
        super("sandbox");
    }

    @Before
    public void setUp() {
        Logger root = (Logger) LoggerFactory.getLogger(CaptureQueue.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    public void shouldAddChargeToQueueAndSubmitForCapture_IfChargeWasPreviouslyAuthorised() {
        String chargeId = authoriseNewCharge();
        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());

        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();

        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Charge [" + chargeId + "] added to capture queue. Message ID [")), is(true));
    }

    @Test
    public void shouldAddChargeToQueueAndSubmitForCapture_IfChargeWasAwaitingCapture() {
        String chargeId = addCharge(AWAITING_CAPTURE_REQUEST, "ref", ZonedDateTime.now().minusHours(48L).plusMinutes(1L), RandomIdGenerator.newId());

        captureApproveUrl = captureUrlForAwaitingCaptureCharge(accountId, chargeId);

        givenSetup()
                .post(captureApproveUrl)
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());

        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();

        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Charge [" + chargeId + "] added to capture queue. Message ID [")), is(true));
    }
}
