package uk.gov.pay.connector.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.SqsConfig;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventQueueTest {

    private EventQueue eventQueue;
    
    private final String eventQueueUrl = "http://some.example/url";

    @Mock
    private SqsQueueService mockSqsQueueService;
    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;
    @Mock
    private SqsConfig sqsConfig;
    @Mock
    private ObjectMapper mockObjectMapper;
    @Mock
    private Event event;

    @Before
    public void setUp() {
        when(mockConnectorConfiguration.getSqsConfig()).thenReturn(sqsConfig);
        when(sqsConfig.getEventQueueUrl()).thenReturn(eventQueueUrl);
        
        eventQueue = new EventQueue(mockSqsQueueService, 
                mockConnectorConfiguration, 
                mockObjectMapper);
    }

    @Test
    public void emitEvent_serialisesTheEventAndSendsToSqs() throws Exception {
        when(mockObjectMapper.writeValueAsString(event)).thenReturn("{~~SERIALIZED~~}");
        
        eventQueue.emitEvent(event);

        verify(mockSqsQueueService).sendMessage(eq(eventQueueUrl), eq("{~~SERIALIZED~~}"));
    }
}
