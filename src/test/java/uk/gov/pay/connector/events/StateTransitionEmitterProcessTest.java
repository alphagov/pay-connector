package uk.gov.pay.connector.events;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.StateTransitionQueue;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.refund.dao.RefundDao;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
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

    private final ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();

    @Mock
    StateTransitionQueue mockStateTransitionQueue;

    @Mock
    EventQueue eventQueue;

    @Mock
    ChargeEventDao chargeEventDao;

    @Mock
    private RefundDao refundDao;
    
    @InjectMocks
    StateTransitionEmitterProcess stateTransitionEmitterProcess;

    @Test
    public void shouldEmitPaymentEventGivenStateTransitionMessageOnQueue() throws Exception {
        Long chargeEventId = 100L;
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(chargeEventId, PaymentCreated.class);

        when(chargeEvent.getChargeEntity()).thenReturn(charge);
        when(mockStateTransitionQueue.poll()).thenReturn(paymentStateTransition);
        when(chargeEventDao.findById(ChargeEventEntity.class, chargeEventId)).thenReturn(Optional.of(chargeEvent));

        stateTransitionEmitterProcess.handleStateTransitionMessages();

        verify(eventQueue).emitEvent(any(PaymentCreated.class));
    }

    @Test
    public void shouldPutPaymentTransitionBackOnQueueIfChargeEventNotFound() {
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(100L, PaymentEvent.class);

        when(mockStateTransitionQueue.poll()).thenReturn(paymentStateTransition);
        when(chargeEventDao.findById(ChargeEventEntity.class, 100L)).thenReturn(Optional.empty());

        stateTransitionEmitterProcess.handleStateTransitionMessages();

        verifyNoMoreInteractions(eventQueue);
        verify(mockStateTransitionQueue).offer(any(PaymentStateTransition.class));
    }

    @Test
    public void shouldPutPaymentTransitionBackOnQueueIfEventEmitFails() throws Exception {
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(100L, PaymentEvent.class);
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);

        when(mockStateTransitionQueue.poll()).thenReturn(paymentStateTransition);
        when(chargeEvent.getUpdated()).thenReturn(ZonedDateTime.now());
        when(chargeEvent.getChargeEntity()).thenReturn(charge);
        when(chargeEventDao.findById(ChargeEventEntity.class, 100L)).thenReturn(Optional.of(chargeEvent));
        doThrow(QueueException.class).when(eventQueue).emitEvent(any());

        stateTransitionEmitterProcess.handleStateTransitionMessages();

        verify(mockStateTransitionQueue).offer(any(PaymentStateTransition.class));
    }

    @Test
    public void shouldNotPutPaymentTransitionBackOnQueueIfItHasExceededMaxAttempts() {
        StateTransitionQueue spyQueue = spy(new StateTransitionQueue());
        StateTransitionEmitterProcess stateTransitionEmitterProcess = new StateTransitionEmitterProcess(spyQueue, eventQueue, chargeEventDao, refundDao);
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(100L, PaymentEvent.class, 0);

        when(chargeEventDao.findById(ChargeEventEntity.class, 100L)).thenReturn(Optional.empty());

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
