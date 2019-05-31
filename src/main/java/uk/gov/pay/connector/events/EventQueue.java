package uk.gov.pay.connector.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.sqs.SqsQueueService;
import uk.gov.pay.connector.util.JsonObjectMapper;

import javax.inject.Inject;

public class EventQueue {

    private final SqsQueueService sqsQueueService;
    private final String eventQueueUrl;
    private final ObjectMapper objectMapper;

    @Inject
    public EventQueue (
            SqsQueueService sqsQueueService,
            ConnectorConfiguration connectorConfiguration, 
            ObjectMapper objectMapper) {
        this.sqsQueueService = sqsQueueService;
        this.eventQueueUrl = connectorConfiguration.getSqsConfig().getEventQueueUrl();
        this.objectMapper = objectMapper;
    }
    
    public void emitEvent(Event event) throws JsonProcessingException, QueueException {
        final String messageBody;
        messageBody = objectMapper.writeValueAsString(event);
        sqsQueueService.sendMessage(eventQueueUrl, messageBody);
    }
}
