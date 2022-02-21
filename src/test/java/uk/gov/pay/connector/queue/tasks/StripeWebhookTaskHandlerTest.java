package uk.gov.pay.connector.queue.tasks;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.queue.tasks.handlers.StripeWebhookTaskHandler;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class StripeWebhookTaskHandlerTest {

    @Mock
    private Appender<ILoggingEvent> logAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    private StripeWebhookTaskHandler underTest;

    @BeforeEach
    void setUp() {
        underTest = new StripeWebhookTaskHandler();
        Logger errorLogger = (Logger) LoggerFactory.getLogger(StripeWebhookTaskHandler.class);
        errorLogger.setLevel(Level.INFO);
        errorLogger.addAppender(logAppender);
    }

    @Test
    void shouldLogReceiptOfWebhook() {
        underTest.process("some-data");
        verify(logAppender).doAppend(loggingEventArgumentCaptor.capture());
        assertThat(loggingEventArgumentCaptor.getValue().getFormattedMessage(), containsString("Processing webhook, ignoring"));
    }
}
