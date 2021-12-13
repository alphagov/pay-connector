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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.QueueMessage;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskQueueMessageHandlerTest {

    @Mock
    private TaskQueue taskQueue;

    @InjectMocks
    TaskQueueMessageHandler taskQueueMessageHandler;

    @Mock
    private Appender<ILoggingEvent> logAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @BeforeEach
    public void setup() {
        Logger errorLogger = (Logger) LoggerFactory.getLogger(TaskQueueMessageHandler.class);
        errorLogger.setLevel(Level.ERROR);
        errorLogger.addAppender(logAppender);
    }

    @Test
    public void shouldMarkMessageAsProcessed() throws QueueException {
        PaymentTaskMessage paymentTaskMessage = setupQueueMessage("payment-external-id-123", "COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT");

        taskQueueMessageHandler.processMessages();

        verify(taskQueue).markMessageAsProcessed(paymentTaskMessage.getQueueMessage());
    }

    @Test
    public void shouldLogErrorIfTaskNameIsNotRecognised() throws QueueException {
        PaymentTaskMessage paymentTaskMessage = setupQueueMessage("payment-external-id-123", "unknown-task-name");

        taskQueueMessageHandler.processMessages();

        verify(taskQueue).markMessageAsProcessed(paymentTaskMessage.getQueueMessage());

        verify(logAppender).doAppend(loggingEventArgumentCaptor.capture());
        assertThat(loggingEventArgumentCaptor.getValue().getFormattedMessage(), containsString("Task [unknown-task-name] is not supported"));
    }

    private PaymentTaskMessage setupQueueMessage(String paymentExternalId, String task) throws QueueException {
        PaymentTask paymentTask = new PaymentTask(paymentExternalId, task);
        QueueMessage mockQueueMessage = mock(QueueMessage.class);
        PaymentTaskMessage paymentTaskMessage = PaymentTaskMessage.of(paymentTask, mockQueueMessage);
        when(taskQueue.retrieveTaskQueueMessages()).thenReturn(List.of(paymentTaskMessage));
        return paymentTaskMessage;
    }
}
