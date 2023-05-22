package uk.gov.pay.connector.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import java.time.ZonedDateTime;

import static java.time.Instant.now;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    EventQueue eventQueue;
    @Mock
    EmittedEventDao emittedEventDao;

    @InjectMocks
    EventService eventService;

    @Test
    void emitEvent() throws QueueException {
        Event event = new PaymentEvent("service-id", true, "external-id", now());
        eventService.emitEvent(event);
        verify(eventQueue).emitEvent(event);
    }

    @Test
    void emitEventShouldRecordEvent() throws QueueException {
        Event event = new PaymentEvent("service-id", true, "external-id", now());
        eventService.emitEvent(event, false);

        verify(eventQueue).emitEvent(event);
    }

    @Test
    void emitEventShouldThrowExceptionIfSwallowExceptionIsFalse() throws QueueException {
        Event event = new PaymentEvent("service-id", true,"external-id", now());
        doThrow(QueueException.class).when(eventQueue).emitEvent(event);
        assertThrows(QueueException.class, ()-> {
            eventService.emitEvent(event, false);    
        });
    }

    @Test
    void emitEventShouldNotThrowExceptionIfSwallowExceptionIsFalse() throws QueueException {
        Event event = new PaymentEvent("service-id", true,"external-id", now());
        doThrow(QueueException.class).when(eventQueue).emitEvent(event);
        eventService.emitEvent(event, true);
        verify(eventQueue).emitEvent(event);
    }

    @Test
    void emitAndRecordEvent_shouldRecordEmission() throws QueueException {
        Event event = new PaymentEvent("service-id", true,"external-id", now());
        eventService.emitAndRecordEvent(event);

        verify(emittedEventDao).recordEmission(event, null);
        verify(eventQueue).emitEvent(event);
    }

    @Test
    void emitAndRecordEvent_shouldRecordEmissionWithDoNotRetryEmitUntilDate() throws QueueException {
        Event event = new PaymentEvent("service-id", true,"external-id", now());
        ZonedDateTime doNotRetryEmitUntilDate = ZonedDateTime.now(UTC);
        eventService.emitAndRecordEvent(event, doNotRetryEmitUntilDate);

        verify(emittedEventDao).recordEmission(event, doNotRetryEmitUntilDate);
        verify(eventQueue).emitEvent(event);
    }

    @Test
    void emitAndRecordEvent_shouldRecordEmissionWithoutEmittedDateForQueueException() throws QueueException {
        Event event = new PaymentCreated("service-id", true,"external-id", null, now());
        doThrow(QueueException.class).when(eventQueue).emitEvent(event);
        eventService.emitAndRecordEvent(event);

        verify(eventQueue).emitEvent(event);
        verify(emittedEventDao, never()).recordEmission(event, null);
        verify(emittedEventDao).recordEmission(event.getResourceType(), event.getResourceExternalId(), event.getEventType(),
                event.getTimestamp(), null);
    }

    @Test
    void emitAndMarkEventAsEmitted() throws QueueException {
        Event event = new PaymentEvent("service-id", true, "external-id", now());
        eventService.emitAndMarkEventAsEmitted(event);

        verify(eventQueue).emitEvent(event);
        verify(emittedEventDao).markEventAsEmitted(event);
    }
}
