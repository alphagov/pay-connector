package uk.gov.pay.connector.queue.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.slf4j.event.Level.ERROR;

@ExtendWith(MockitoExtension.class)
class TaskQueueTest {

    @RegisterExtension
    LogCapturer errorLogs = LogCapturer.create()
            .forLevel(ERROR)
            .captureForType(TaskQueue.class);

    @Mock
    SqsQueueService sqsQueueService;

    @Mock
    ConnectorConfiguration connectorConfiguration;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TaskQueueConfig taskQueueConfig = mock(TaskQueueConfig.class);
        SqsConfig sqsConfig = mock(SqsConfig.class);
        when(sqsConfig.getTaskQueueUrl()).thenReturn("");
        when(taskQueueConfig.getFailedMessageRetryDelayInSeconds()).thenReturn(3600);
        when(connectorConfiguration.getSqsConfig()).thenReturn(sqsConfig);
        when(connectorConfiguration.getTaskQueueConfig()).thenReturn(taskQueueConfig);
        when(taskQueueConfig.getDeliveryDelayInSeconds()).thenReturn(2);
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

        errorLogs.assertContains("Error parsing message from tasks queue");
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
