package uk.gov.pay.connector.events;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentCreatedEventDetails;
import uk.gov.pay.connector.events.exception.EventCreationException;
import uk.gov.pay.connector.events.model.EventFactory;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.StateTransitionQueue;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StateTransitionEmitterProcessTest {
    @Mock
    StateTransitionQueue stateTransitionQueue;

    @Mock
    StateTransitionQueueMetricEmitter stateTransitionQueueMetricEmitter;

    @Mock
    private EventFactory eventFactory;
    
    @Mock
    EventService mockEventService;

    @InjectMocks
    StateTransitionEmitterProcess stateTransitionEmitterProcess;

    @Test
    public void shouldEmitPaymentEventGivenStateTransitionMessageOnQueue() throws Exception {
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(100L, PaymentCreated.class);
        when(eventFactory.createEvents(any(PaymentStateTransition.class))).thenReturn(List.of(
                new PaymentCreated(
                        "id",
                        mock(PaymentCreatedEventDetails.class),
                        ZonedDateTime.now()
                )));
        when(stateTransitionQueue.poll(anyLong(), any(TimeUnit.class))).thenReturn(paymentStateTransition);

        stateTransitionEmitterProcess.handleStateTransitionMessages();

        verify(mockEventService).emitAndMarkEventAsEmitted(any(PaymentCreated.class));
    }

    @Test
    public void shouldPutPaymentTransitionBackOnQueueIfEventCreationFails() throws Exception {
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(100L, PaymentEvent.class);
        when(stateTransitionQueue.poll(anyLong(), any(TimeUnit.class))).thenReturn(paymentStateTransition);
        when(eventFactory.createEvents(any(PaymentStateTransition.class))).thenThrow(EventCreationException.class);

        stateTransitionEmitterProcess.handleStateTransitionMessages();

        verifyNoMoreInteractions(mockEventService);
        verify(stateTransitionQueue).offer(any(PaymentStateTransition.class));
    }

    @Test
    public void shouldPutPaymentTransitionBackOnQueueIfEventEmitFails() throws Exception {
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(100L, PaymentEvent.class);
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        when(stateTransitionQueue.poll(anyLong(), any(TimeUnit.class))).thenReturn(paymentStateTransition);
        when(eventFactory.createEvents(any(PaymentStateTransition.class))).thenReturn(List.of(
                new PaymentCreated(
                        "id",
                        mock(PaymentCreatedEventDetails.class),
                        ZonedDateTime.now()
                )));
        doThrow(QueueException.class).when(mockEventService).emitAndMarkEventAsEmitted(any());

        stateTransitionEmitterProcess.handleStateTransitionMessages();

        verify(stateTransitionQueue).offer(any(PaymentStateTransition.class));
    }


    @Test
    public void shouldNotPutPaymentTransitionBackOnQueueIfItHasExceededMaxAttempts() throws Exception {
        StateTransitionQueue spyQueue = spy(new StateTransitionQueue());
        StateTransitionEmitterProcess stateTransitionEmitterProcess = new StateTransitionEmitterProcess(spyQueue, eventFactory, stateTransitionQueueMetricEmitter, mockEventService);
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(100L, PaymentEvent.class, 0);

        when(eventFactory.createEvents(any(PaymentStateTransition.class))).thenThrow(EventCreationException.class);

        spyQueue.offer(paymentStateTransition);

        int maximumStateTransitionMessageRetries = 10;

        // try until message attempt limit, factor in initial offer
        for (int i = 0; i < maximumStateTransitionMessageRetries; i++) {
            verify(spyQueue, times(i + 1)).offer(any(PaymentStateTransition.class));
            stateTransitionEmitterProcess.handleStateTransitionMessages();
        }

        verify(spyQueue, atMost(maximumStateTransitionMessageRetries)).offer(any());
    }
}
