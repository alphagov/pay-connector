package uk.gov.pay.connector.events;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;
import uk.gov.pay.connector.queue.QueueException;

import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.now;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class EventServiceTest {

    @Mock
    EventQueue eventQueue;
    @Mock
    EmittedEventDao emittedEventDao;

    @InjectMocks
    EventService eventService;

    @Test
    public void emitAndRecordEvent_shouldRecordEmission() throws QueueException {
        Event event = new PaymentEvent("external-id", now());
        eventService.emitAndRecordEvent(event);

        verify(emittedEventDao).recordEmission(event, null);
        verify(eventQueue).emitEvent(event);
    }

    @Test
    public void emitAndRecordEvent_shouldRecordEmissionWithDoNotRetryEmitUntilDate() throws QueueException {
        Event event = new PaymentEvent("external-id", now());
        ZonedDateTime doNotRetryEmitUntilDate = now(UTC);
        eventService.emitAndRecordEvent(event, doNotRetryEmitUntilDate);

        verify(emittedEventDao).recordEmission(event, doNotRetryEmitUntilDate);
        verify(eventQueue).emitEvent(event);
    }

    @Test
    public void emitAndRecordEvent_shouldRecordEmissionWithoutEmittedDateForQueueException() throws QueueException {
        Event event = new PaymentCreated("external-id", null, now());
        doThrow(QueueException.class).when(eventQueue).emitEvent(event);
        eventService.emitAndRecordEvent(event);

        verify(eventQueue).emitEvent(event);
        verify(emittedEventDao, never()).recordEmission(event, null);
        verify(emittedEventDao).recordEmission(event.getResourceType(), event.getResourceExternalId(), event.getEventType(), event.getTimestamp(), null);
    }

    @Test
    public void emitAndMarkEventAsEmitted() throws QueueException {
        Event event = new PaymentEvent("external-id", now());
        eventService.emitAndMarkEventAsEmitted(event);

        verify(eventQueue).emitEvent(event);
        verify(emittedEventDao).markEventAsEmitted(event);
    }
}
