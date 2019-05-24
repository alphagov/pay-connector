package uk.gov.pay.connector.it.sqs;

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
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureMessageProcess;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml",
        configOverrides = {@ConfigOverride(key = "captureProcessConfig.captureUsingSQS", value = "true")},
        withDockerSQS = true
)
public class CaptureWithSqsQueueITest extends ChargingITestBase {

    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);

    public CaptureWithSqsQueueITest() {
        super("sandbox");
    }

    @Before
    public void setUp() {
        Logger cardCaptureMessageProcessLogger = (Logger) LoggerFactory.getLogger(CardCaptureMessageProcess.class);
        cardCaptureMessageProcessLogger.setLevel(Level.INFO);
        cardCaptureMessageProcessLogger.addAppender(mockAppender);
    }

    @Test
    public void shouldPickUpCaptureMessageFromQueue() {
        String chargeId = getIdOfCapturePutInTheQueue();

        verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();

        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Charge capture message received - " + chargeId)), is(true));
    }

    // TODO (mj): simple way of putting a message in the queue given we don't have proper setup - ideally
    //              should be replaced with QueueFixture + QueueHelper (similar to database set up)
    private String getIdOfCapturePutInTheQueue() {
        String chargeId = authoriseNewCharge();
        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        return chargeId;
    }

}
