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
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.SqsConfig;
import uk.gov.pay.connector.app.config.TaskQueueConfig;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.model.QueueMessage;
import uk.gov.service.payments.commons.queue.sqs.SqsQueueService;

import java.util.List;

import static com.jayway.jsonassert.impl.matcher.IsCollectionWithSize.hasSize;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskQueueTest {

    @Mock
    SqsQueueService sqsQueueService;

    @Mock
    ConnectorConfiguration connectorConfiguration;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    private static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        TaskQueueConfig taskQueueConfig = mock(TaskQueueConfig.class);
        SqsConfig sqsConfig = mock(SqsConfig.class);
        when(sqsConfig.getTaskQueueUrl()).thenReturn("");
        when(taskQueueConfig.getFailedMessageRetryDelayInSeconds()).thenReturn(3600);
        when(connectorConfiguration.getSqsConfig()).thenReturn(sqsConfig);
        when(connectorConfiguration.getTaskQueueConfig()).thenReturn(taskQueueConfig);
        when(taskQueueConfig.getDeliveryDelayInSeconds()).thenReturn(2);
        Logger logger = (Logger) LoggerFactory.getLogger(TaskQueue.class);
        logger.setLevel(Level.ERROR);
        logger.addAppender(mockAppender);
    }

    @Test
    void shouldParseChargeIdReceivedFromQueueGivenWellFormattedJSON() throws QueueException {
        String validJsonMessage = "{ \"data\": \"payload data\",\"task\":\"collect_fee_for_stripe_failed_payment\"}";
        SendMessageResponse messageResult = mock(SendMessageResponse.class);
        List<QueueMessage> messages = List.of(QueueMessage.of(messageResult, validJsonMessage));
        when(sqsQueueService.receiveMessages(anyString(), anyString())).thenReturn(messages);
        TaskQueue queue = new TaskQueue(sqsQueueService, connectorConfiguration, objectMapper);
        List<TaskMessage> taskMessages = queue.retrieveTaskQueueMessages();

        assertNotNull(taskMessages);
        assertEquals("payload data", taskMessages.getFirst().getTask().getData());
        assertEquals(TaskType.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT, taskMessages.getFirst().getTask().getTaskType());
    }

    @Test
    void shouldParseChargeIdReceivedFromQueueGivenWellFormattedJSON_OldFormat() throws QueueException {
        String validJsonMessage = "{ \"payment_external_id\": \"external-id-123\",\"task\":\"collect_fee_for_stripe_failed_payment\"}";
        SendMessageResponse messageResult = mock(SendMessageResponse.class);
        List<QueueMessage> messages = List.of(QueueMessage.of(messageResult, validJsonMessage));
        when(sqsQueueService.receiveMessages(anyString(), anyString())).thenReturn(messages);
        TaskQueue queue = new TaskQueue(sqsQueueService, connectorConfiguration, objectMapper);
        List<TaskMessage> taskMessages = queue.retrieveTaskQueueMessages();

        assertNotNull(taskMessages);
        assertEquals("external-id-123", taskMessages.getFirst().getTask().getPaymentExternalId());
        assertEquals(TaskType.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT, taskMessages.getFirst().getTask().getTaskType());
    }

    @Test
    void shouldLogErrorWhenTaskTypeDoesntExist() throws QueueException {
        String invalidJsonMessage = "{ \"data\": \"payload data\",\"task\":\"foo\"}";
        String validJsonMessage = "{ \"data\": \"payload data\",\"task\":\"collect_fee_for_stripe_failed_payment\"}";
        SendMessageResponse messageResult = mock(SendMessageResponse.class);
        List<QueueMessage> messages = List.of(QueueMessage.of(messageResult, invalidJsonMessage), QueueMessage.of(messageResult, validJsonMessage));
        when(sqsQueueService.receiveMessages(anyString(), anyString())).thenReturn(messages);
        TaskQueue queue = new TaskQueue(sqsQueueService, connectorConfiguration, objectMapper);
        List<TaskMessage> taskMessages = queue.retrieveTaskQueueMessages();

        assertThat(taskMessages, hasSize(1));
        assertEquals("payload data", taskMessages.getFirst().getTask().getData());
        assertEquals(TaskType.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT, taskMessages.getFirst().getTask().getTaskType());

        verify(mockAppender).doAppend(loggingEventArgumentCaptor.capture());
        LoggingEvent loggingEvent = loggingEventArgumentCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.ERROR));
        assertThat(loggingEvent.getMessage(), is("Error parsing message from tasks queue"));
    }

    @Test
    void shouldSendValidSerialisedChargeToQueue() throws QueueException, JsonProcessingException {
        when(sqsQueueService.sendMessage(anyString(), anyString(), anyInt())).thenReturn(mock(QueueMessage.class));

        TaskQueue queue = new TaskQueue(sqsQueueService, connectorConfiguration, objectMapper);
        Task task = new Task("payload data", TaskType.COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT);
        queue.addTaskToQueue(task);

        verify(sqsQueueService).sendMessage(connectorConfiguration.getSqsConfig().getTaskQueueUrl(),
                "{\"data\":\"payload data\",\"task\":\"collect_fee_for_stripe_failed_payment\"}", 2);
    }

    @Test
    void addTaskToQueueWithDelay_shouldSendValidSerialisedDataToQueueWithSpecifiedDelay() throws QueueException, JsonProcessingException {
        when(sqsQueueService.sendMessage(anyString(), anyString(), anyInt())).thenReturn(mock(QueueMessage.class));

        TaskQueue queue = new TaskQueue(sqsQueueService, connectorConfiguration, objectMapper);
        Task task = new Task("payload data", TaskType.RETRY_FAILED_PAYMENT_OR_REFUND_EMAIL);
        queue.addTaskToQueue(task, 100);

        verify(sqsQueueService).sendMessage(connectorConfiguration.getSqsConfig().getTaskQueueUrl(),
                "{\"data\":\"payload data\",\"task\":\"retry_failed_payment_or_refund_email\"}", 100);
    }
}
