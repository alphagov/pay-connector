package uk.gov.pay.connector.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.sqs.SqsQueueService;

import javax.inject.Inject;

public class EventQueue {

    private final SqsQueueService sqsQueueService;
    private final String eventQueueUrl;
    private final Boolean eventQueueEnabled;

    @Inject
    public EventQueue (
            SqsQueueService sqsQueueService,
            ConnectorConfiguration connectorConfiguration
    ) {
        this.sqsQueueService = sqsQueueService;
        this.eventQueueUrl = connectorConfiguration.getSqsConfig().getEventQueueUrl();
        this.eventQueueEnabled = connectorConfiguration.getEventQueueConfig().getEventQueueEnabled();
    }

    public void emitEvent(Event event) throws QueueException {
        // send to Nats queue payment.event
        if (eventQueueEnabled) {
            try {
                sqsQueueService.sendMessage(eventQueueUrl, event.toJsonString());
            } catch (JsonProcessingException e) {
                throw new QueueException(String.format("Error serialising event to json: %s", e.getMessage()));
            }
        }
    }
}
