package uk.gov.pay.connector.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import jakarta.inject.Inject;
import java.time.Instant;
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

    public void emitEvent(Event event) {
        try {
            this.emitEvent(event, true);
        } catch (QueueException e) {
            // this exception won't be reached
        }
    }

    /**
     * This method is used principally for emitting events when the records (expunged charges, stripe payout and similar)
     * does not exist in connector database and does not record the event in EmittedEventDao.
     *
     * <u>QueueException</u>
     * In the event of a QueueException we have taken the decision not to retry the emitted event in some cases because:
     * - the likelihood of failing to send a message to the highly available SQS service is very low.
     * - emitted events are tightly coupled to entities in the database and doing the work to re-send an event for
     * event related to expunged entity is more work that doesn't justify the problem we're trying to solve, namely
     * processing notifications from ePDQ where a state transition has to be forced (PP-6201).
     * <br>
     * To achieve this, set <b>swallowException</b> to true so QueueException will be ignored.
     *
     * @param event
     * @param swallowException set to true to not throw QueueException to calling methods.
     * @throws QueueException
     */
    public void emitEvent(Event event, boolean swallowException) throws QueueException {
        try {
            eventQueue.emitEvent(event);
        } catch (QueueException e) {
            logger.error("Failed to emit event {} due to {} [externalId={}]", event.getEventType(),
                    e.getMessage(),
                    event.getResourceExternalId());
            if (!swallowException) {
                throw e;
            }
        }
    }

    public void emitAndRecordEvent(Event event, ZonedDateTime doNotRetryEmitUntilDate) {
        try {
            eventQueue.emitEvent(event);
            emittedEventDao.recordEmission(event, doNotRetryEmitUntilDate);
        } catch (QueueException e) {
            emittedEventDao.recordEmission(event.getResourceType(), event.getResourceExternalId(),
                    event.getEventType(), event.getTimestamp(), doNotRetryEmitUntilDate);
            logger.error("Failed to emit event {} due to {} [externalId={}]",
                    event.getEventType(), e.getMessage(), event.getResourceExternalId());
        }
    }

    public void emitAndRecordEvent(Event event) {
        this.emitAndRecordEvent(event, null);
    }

    public void emitAndMarkEventAsEmitted(Event event) throws QueueException {
        eventQueue.emitEvent(event);
        emittedEventDao.markEventAsEmitted(event);
    }

    public void recordOfferedEvent(ResourceType resourceType, String externalId, String eventType, Instant eventDate) {
        this.recordOfferedEvent(resourceType, externalId, eventType, eventDate, null);
    }

    public void recordOfferedEvent(ResourceType resourceType, String externalId, String eventType,
                                   Instant eventDate, ZonedDateTime doNotRetryEmitUntilDate) {
        emittedEventDao.recordEmission(resourceType, externalId, eventType, eventDate, doNotRetryEmitUntilDate);
    }
}
