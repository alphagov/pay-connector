package uk.gov.pay.connector.queue;

import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ChargeAsyncOperationsConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.SqsConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.queue.capture.ChargeAsyncOperationsQueue;
import uk.gov.pay.connector.queue.capture.ChargeAsyncOperationsMessage;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.model.QueueMessage;
import uk.gov.service.payments.commons.queue.sqs.SqsQueueService;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ChargeAsyncOperationsQueueTest {

    @Mock
    SqsQueueService sqsQueueService;

    @Mock
    ConnectorConfiguration connectorConfiguration;

    private static ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() throws Exception {
        String validJsonMessage = "{ \"chargeId\": \"my-charge-id\"}";
        SendMessageResult messageResult = mock(SendMessageResult.class);

        List<QueueMessage> messages = Arrays.asList(
                QueueMessage.of(messageResult, validJsonMessage)
        );
        ChargeAsyncOperationsConfig chargeAsyncOperationsConfig = mock(ChargeAsyncOperationsConfig.class);
        SqsConfig sqsConfig = mock(SqsConfig.class);
        when(sqsConfig.getCaptureQueueUrl()).thenReturn("");
        when(chargeAsyncOperationsConfig.getFailedCaptureRetryDelayInSeconds()).thenReturn(3600);
        when(connectorConfiguration.getSqsConfig()).thenReturn(sqsConfig);
        when(connectorConfiguration.getChargeAsyncOperationsConfig()).thenReturn(chargeAsyncOperationsConfig);
        when(sqsQueueService.receiveMessages(anyString(), anyString())).thenReturn(messages);
    }

    @Test
    public void shouldParseChargeIdReceivedFromQueueGivenWellFormattedJSON() throws QueueException {
        ChargeAsyncOperationsQueue queue = new ChargeAsyncOperationsQueue(sqsQueueService, connectorConfiguration, objectMapper);
        List<ChargeAsyncOperationsMessage> chargeAsyncOperationsMessages = queue.retrieveAsyncOperations();

        assertNotNull(chargeAsyncOperationsMessages);
        assertEquals("my-charge-id", chargeAsyncOperationsMessages.get(0).getChargeId());
    }

    @Test
    public void shouldSendValidSerialisedChargeToQueue() throws QueueException, JsonProcessingException {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().withExternalId("charge-id").build();
        when(sqsQueueService.sendMessage(anyString(), anyString())).thenReturn(mock(QueueMessage.class));

        ChargeAsyncOperationsQueue queue = new ChargeAsyncOperationsQueue(sqsQueueService, connectorConfiguration, objectMapper);
        queue.sendForCapture(chargeEntity);

        verify(sqsQueueService).sendMessage(connectorConfiguration.getSqsConfig().getCaptureQueueUrl(),
                "{\"operationKey\":\"CAPTURE\",\"chargeId\":\"charge-id\"}");
    }
}
