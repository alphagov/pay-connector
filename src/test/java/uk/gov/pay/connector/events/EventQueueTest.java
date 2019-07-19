package uk.gov.pay.connector.events;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.EventQueueConfig;
import uk.gov.pay.connector.app.SqsConfig;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    private EventQueueConfig eventQueueConfig;
    @Mock
    private Event event;

    @Before
    public void setUp() {
        when(mockConnectorConfiguration.getSqsConfig()).thenReturn(sqsConfig);
        when(mockConnectorConfiguration.getEventQueueConfig()).thenReturn(eventQueueConfig);
        when(sqsConfig.getEventQueueUrl()).thenReturn(eventQueueUrl);
    }

    @Test
    public void emitEvent_serialisesTheEventAndSendsToSqs() throws Exception {
        when(eventQueueConfig.getEventQueueEnabled()).thenReturn(true);
        eventQueue = new EventQueue(mockSqsQueueService,
                mockConnectorConfiguration);
        when(event.toJsonString()).thenReturn("{~~SERIALIZED~~}");

        eventQueue.emitEvent(event);

        verify(mockSqsQueueService).sendMessage(eq(eventQueueUrl), eq("{~~SERIALIZED~~}"));
    }

    @Test
    public void emitEvent_doesNotEmitIfFeatureFlagIsFalse() throws Exception {
        when(eventQueueConfig.getEventQueueEnabled()).thenReturn(false);
        eventQueue = new EventQueue(mockSqsQueueService,
                mockConnectorConfiguration);
        
        eventQueue.emitEvent(event);

        verifyNoMoreInteractions(mockSqsQueueService);
    }
}
