package uk.gov.pay.connector.queue;

import com.amazonaws.services.sqs.model.SendMessageResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.paymentprocessor.service.CardCaptureMessageProcess;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CaptureQueueTest {
    
    @Mock
    SqsQueueService sqsQueueService;
    
    @Mock
    ConnectorConfiguration connectorConfiguration;
    
    @Mock
    CardCaptureMessageProcess cardCaptureMessageProcess;

    @Mock
    SendMessageResult messageResult;
    

    @Before
    public void setUp() throws Exception {  
    }
   
    @Test
    public void shouldParseChargeIdGivenWellFormattedJSON() throws QueueException {
        String validJsonMessage = "{ \"chargeId\": \"my-charge-id\"}";
        when(messageResult.getMessageId()).thenReturn("ID");
        
        List<QueueMessage> messages = Arrays.asList(
                QueueMessage.of(messageResult, validJsonMessage)
        );
        when(sqsQueueService.receiveMessages(anyString(), anyString())).thenReturn(messages);
        
        CaptureQueue queue = new CaptureQueue(sqsQueueService, connectorConfiguration, cardCaptureMessageProcess);
    }
}
