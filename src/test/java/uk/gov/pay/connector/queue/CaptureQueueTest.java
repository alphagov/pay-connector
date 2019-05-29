package uk.gov.pay.connector.queue;

import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.CaptureProcessConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.SqsConfig;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CaptureQueueTest {

    @Mock
    SqsQueueService sqsQueueService;

    @Mock
    ConnectorConfiguration connectorConfiguration;

    JsonObjectMapper jsonObjectMapper;

    @Before
    public void setUp() throws Exception {
        String validJsonMessage = "{ \"chargeId\": \"my-charge-id\"}";
        SendMessageResult messageResult = mock(SendMessageResult.class);

        List<QueueMessage> messages = Arrays.asList(
                QueueMessage.of(messageResult, validJsonMessage)
        );
        jsonObjectMapper = new JsonObjectMapper(new ObjectMapper());
        CaptureProcessConfig captureProcessConfig = mock(CaptureProcessConfig.class);
        SqsConfig sqsConfig = mock(SqsConfig.class);
        when(sqsConfig.getCaptureQueueUrl()).thenReturn("");
        when(captureProcessConfig.getFailedCaptureRetryDelayInSeconds()).thenReturn(3600);
        when(connectorConfiguration.getSqsConfig()).thenReturn(sqsConfig);
        when(connectorConfiguration.getCaptureProcessConfig()).thenReturn(captureProcessConfig);
        when(sqsQueueService.receiveMessages(anyString(), anyString())).thenReturn(messages);
    }

    @Test
    public void shouldParseChargeIdGivenWellFormattedJSON() throws QueueException {
        CaptureQueue queue = new CaptureQueue(sqsQueueService, connectorConfiguration, jsonObjectMapper);
        List<ChargeCaptureMessage> chargeCaptureMessages = queue.retrieveChargesForCapture();

        assertNotNull(chargeCaptureMessages);
        assertEquals("my-charge-id", chargeCaptureMessages.get(0).getChargeId());
    }
}
