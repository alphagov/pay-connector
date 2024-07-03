package uk.gov.pay.connector.it.resources;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.queue.capture.CaptureQueue;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.Instant;
import java.util.List;

import static io.dropwizard.testing.ConfigOverride.config;
import static java.lang.String.format;
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
import static uk.gov.pay.connector.it.base.ITestBaseExtension.SERVICE_ID;

public class CardResourceCaptureWithSqsQueueIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(config("captureProcessConfig.backgroundProcessingEnabled", "false"));
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());

    private Appender<ILoggingEvent> mockAppender = mock(Appender.class);
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
    
    @BeforeEach
    void setUpLogger() {
        Logger root = (Logger) LoggerFactory.getLogger(CaptureQueue.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Nested
    class CaptureApproveByChargeId {
        @Test
        void shouldAddChargeToQueueAndSubmitForCapture_IfChargeWasPreviouslyAuthorised() {
            String chargeId = testBaseExtension.authoriseNewCharge();
            app.givenSetup()
                    .post(ITestBaseExtension.captureChargeUrlFor(chargeId))
                    .then()
                    .statusCode(204);

            testBaseExtension.assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
            testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());

            verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();

            assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Charge [" + chargeId + "] added to capture queue. Message ID [")), is(true));
        }
    }

    @Nested
    class DelayedCaptureApproveByChargeIdAndAccountId {
        @Test
        void shouldAddChargeToQueueAndSubmitForCapture_IfChargeWasAwaitingCapture_whenCaptureByChargeIdAndAccountId() {
            String chargeId = testBaseExtension.addCharge(AWAITING_CAPTURE_REQUEST, "ref", Instant.now().minus(48, HOURS).plus(1, MINUTES), RandomIdGenerator.newId());
            
            app.givenSetup()
                    .post(format("/v1/api/accounts/%s/charges/%s/capture", testBaseExtension.getAccountId(), chargeId))
                    .then()
                    .statusCode(204);

            testBaseExtension.assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
            testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());

            verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();

            assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Charge [" + chargeId + "] added to capture queue. Message ID [")), is(true));
        }
    }

    @Nested
    class DelayedCaptureApproveByServiceIdAndAccountType {
        @Test
        void shouldAddChargeToQueueAndSubmitForCapture_IfChargeWasAwaitingCapture_whenCaptureByChargeId() {
            String chargeId = testBaseExtension.addCharge(AWAITING_CAPTURE_REQUEST, "ref", Instant.now().minus(48, HOURS).plus(1, MINUTES), RandomIdGenerator.newId());

            app.givenSetup()
                    .post(format("/v1/api/service/%s/account/test/charges/%s/capture", SERVICE_ID, chargeId))
                    .then()
                    .statusCode(204);

            testBaseExtension.assertFrontendChargeStatusIs(chargeId, CAPTURE_APPROVED.getValue());
            testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());

            verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();

            assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Charge [" + chargeId + "] added to capture queue. Message ID [")), is(true));
        }
    }
}
