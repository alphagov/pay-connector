package uk.gov.pay.connector.queue.tasks;

import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.SqsConfig;
import uk.gov.pay.connector.app.config.TaskQueueConfig;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.model.QueueMessage;
import uk.gov.service.payments.commons.queue.sqs.SqsQueueService;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TaskQueueTest {

    @Mock
    SqsQueueService sqsQueueService;

    @Mock
    ConnectorConfiguration connectorConfiguration;

    private static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        TaskQueueConfig taskQueueConfig = mock(TaskQueueConfig.class);
        SqsConfig sqsConfig = mock(SqsConfig.class);
        when(sqsConfig.getTaskQueueUrl()).thenReturn("");
        when(taskQueueConfig.getFailedMessageRetryDelayInSeconds()).thenReturn(3600);
        when(connectorConfiguration.getSqsConfig()).thenReturn(sqsConfig);
        when(connectorConfiguration.getTaskQueueConfig()).thenReturn(taskQueueConfig);
    }

    @Test
    public void shouldParseChargeIdReceivedFromQueueGivenWellFormattedJSON() throws QueueException {
        String validJsonMessage = "{ \"payment_external_id\": \"external-id-123\",\"task\":\"COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT\"}";
        SendMessageResult messageResult = mock(SendMessageResult.class);
        List<QueueMessage> messages = Arrays.asList(QueueMessage.of(messageResult, validJsonMessage));
        when(sqsQueueService.receiveMessages(anyString(), anyString())).thenReturn(messages);
        TaskQueue queue = new TaskQueue(sqsQueueService, connectorConfiguration, objectMapper);
        List<PaymentTaskMessage> paymentTasks = queue.retrieveTaskQueueMessages();

        assertNotNull(paymentTasks);
        assertEquals("external-id-123", paymentTasks.get(0).getPaymentExternalId());
        assertEquals("COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT", paymentTasks.get(0).getTask());
    }

    @Test
    public void shouldSendValidSerialisedChargeToQueue() throws QueueException, JsonProcessingException {
        when(sqsQueueService.sendMessage(anyString(), anyString())).thenReturn(mock(QueueMessage.class));

        TaskQueue queue = new TaskQueue(sqsQueueService, connectorConfiguration, objectMapper);
        PaymentTask task = new PaymentTask("external-id-123", "COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT");
        queue.addTaskToQueue(task);

        verify(sqsQueueService).sendMessage(connectorConfiguration.getSqsConfig().getTaskQueueUrl(),
                "{\"payment_external_id\":\"external-id-123\",\"task\":\"COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT\"}");
    }
}
