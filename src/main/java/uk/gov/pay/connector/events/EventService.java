package uk.gov.pay.connector.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.queue.QueueException;

import javax.inject.Inject;
import java.time.ZonedDateTime;

public class EventService {
    private static final Logger logger = LoggerFactory.getLogger(EventService.class);

    private EventQueue eventQueue;
    private EmittedEventDao emittedEventDao;

    @Inject
    public EventService(EventQueue eventQueue, EmittedEventDao emittedEventDao) {
        this.eventQueue = eventQueue;
        this.emittedEventDao = emittedEventDao;
    }

    public void emitAndRecordEvent(Event event) {
        try {
            eventQueue.emitEvent(event);
            emittedEventDao.recordEmission(event);
        } catch (QueueException e) {
            emittedEventDao.recordEmission(event.getResourceType(), event.getResourceExternalId(), event.getEventType(), event.getTimestamp());
            logger.error("Error emitting {} event: {}", event.getEventType(), e.getMessage());
        }
    }

    public void emitAndMarkEventAsEmitted(Event event) throws QueueException {
        eventQueue.emitEvent(event);
        emittedEventDao.markEventAsEmitted(event);
    }

    public void recordOfferedEvent(ResourceType resourceType, String externalId, String eventType, ZonedDateTime eventDate) {
        emittedEventDao.recordEmission(resourceType, externalId, eventType, eventDate);
    }
}
