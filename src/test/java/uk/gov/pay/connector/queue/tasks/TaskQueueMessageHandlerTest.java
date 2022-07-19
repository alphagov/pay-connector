package uk.gov.pay.connector.queue.tasks;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.queue.tasks.handlers.AuthoriseWithUserNotPresentHandler;
import uk.gov.pay.connector.queue.tasks.handlers.CollectFeesForFailedPaymentsTaskHandler;
import uk.gov.pay.connector.queue.tasks.handlers.StripeWebhookTaskHandler;
import uk.gov.pay.connector.queue.tasks.model.PaymentTaskData;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.model.QueueMessage;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskQueueMessageHandlerTest {
    
    @Mock
    private TaskQueue taskQueue;
    
    @Mock
    private CollectFeesForFailedPaymentsTaskHandler collectFeesForFailedPaymentsTaskHandler;

    @Mock
    private StripeWebhookTaskHandler stripeWebhookTaskHandler;

    @Mock
    private AuthoriseWithUserNotPresentHandler authoriseWithUserNotPresentHandler;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;
    
    private TaskQueueMessageHandler taskQueueMessageHandler;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String chargeExternalId = "a-charge-external-id";

    @BeforeEach
    public void setup() {
        taskQueueMessageHandler = new TaskQueueMessageHandler(
                taskQueue,
                collectFeesForFailedPaymentsTaskHandler,
                stripeWebhookTaskHandler,
                authoriseWithUserNotPresentHandler,
                objectMapper);

        Logger logger = (Logger) LoggerFactory.getLogger(TaskQueueMessageHandler.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(mockAppender);
    }

    @Test
    public void shouldProcessNewCollectFeeTask() throws Exception {
        var paymentTaskData = new PaymentTaskData(chargeExternalId);
        String data = objectMapper.writeValueAsString(paymentTaskData);
        TaskMessage taskMessage = setupQueueMessage(data, TaskType.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT);
        taskQueueMessageHandler.processMessages();
        verify(collectFeesForFailedPaymentsTaskHandler).collectAndPersistFees(paymentTaskData);
        verify(taskQueue).markMessageAsProcessed(taskMessage.getQueueMessage());

        verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.INFO));
        assertThat(loggingEvent.getFormattedMessage(), is("Processing [collect_fee_for_stripe_failed_payment] task."));
    }

    @Test
    public void shouldProcessOldCollectFeeTask() throws Exception {
        Task oldFormatTask = mock(Task.class);
        QueueMessage mockQueueMessage = mock(QueueMessage.class);
        TaskMessage taskMessage = TaskMessage.of(oldFormatTask, mockQueueMessage);

        when(taskQueue.retrieveTaskQueueMessages()).thenReturn(List.of(taskMessage));
        when(oldFormatTask.getPaymentExternalId()).thenReturn(chargeExternalId);
        when(oldFormatTask.getTaskType()).thenReturn(TaskType.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT);
        
        taskQueueMessageHandler.processMessages();
        var paymentTaskData = new PaymentTaskData(chargeExternalId);
        verify(collectFeesForFailedPaymentsTaskHandler).collectAndPersistFees(paymentTaskData);
        verify(taskQueue).markMessageAsProcessed(taskMessage.getQueueMessage());

        verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.INFO));
        assertThat(loggingEvent.getFormattedMessage(), is("Processing [collect_fee_for_stripe_failed_payment] task."));
    }

    @Test
    public void shouldProcessDisputeCreatedTask() throws Exception {
        TaskMessage taskMessage = setupQueueMessage("{ \"key\": \"value\"}", TaskType.HANDLE_STRIPE_WEBHOOK_NOTIFICATION);
        taskQueueMessageHandler.processMessages();
        verify(stripeWebhookTaskHandler).process(any(StripeNotification.class));
        verify(taskQueue).markMessageAsProcessed(taskMessage.getQueueMessage());
    }

    @Test
    public void shouldProcessAuthoriseWithUserNotPresentTask() throws QueueException {
        TaskMessage taskMessage = setupQueueMessage("{ \"payment_external_id\": \"external-charge-id\"}", TaskType.AUTHORISE_WITH_USER_NOT_PRESENT);
        taskQueueMessageHandler.processMessages();
        verify(authoriseWithUserNotPresentHandler).process("external-charge-id");
        verify(taskQueue).markMessageAsProcessed(taskMessage.getQueueMessage());
    }

    private TaskMessage setupQueueMessage(String data, TaskType taskType) throws QueueException {
        Task paymentTask = new Task(data, taskType);
        QueueMessage mockQueueMessage = mock(QueueMessage.class);
        TaskMessage taskMessage = TaskMessage.of(paymentTask, mockQueueMessage);
        when(taskQueue.retrieveTaskQueueMessages()).thenReturn(List.of(taskMessage));
        return taskMessage;
    }
}
