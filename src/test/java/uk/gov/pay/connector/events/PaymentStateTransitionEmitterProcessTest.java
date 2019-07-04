package uk.gov.pay.connector.events;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.PaymentStateTransitionQueue;
import uk.gov.pay.connector.queue.QueueException;
import unfiltered.response.link.Payment;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PaymentStateTransitionEmitterProcessTest {

    @Mock
    PaymentStateTransitionQueue paymentStateTransitionQueue;

    @Mock
    EventQueue eventQueue;

    @Mock
    ChargeEventDao chargeEventDao;

    @InjectMocks
    PaymentStateTransitionEmitterProcess paymentStateTransitionEmitterProcess;

    @Test
    public void shouldEmitPaymentEventGivenStateTransitionMessageOnQueue() throws Exception {
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(100L, PaymentEvent.class);
        when(paymentStateTransitionQueue.poll()).thenReturn(paymentStateTransition);

        paymentStateTransitionEmitterProcess.handleStateTransitionMessages();

        verify(eventQueue).emitEvent(any());
    }

    @Test
    public void shouldPutPaymentTransitionBackOnQueueIfChargeEventNotFound() throws Exception {
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(100L, PaymentEvent.class);
        when(paymentStateTransitionQueue.poll()).thenReturn(paymentStateTransition);
        when(chargeEventDao.findById(ChargeEventEntity.class, 100L)).thenReturn(Optional.empty());

        paymentStateTransitionEmitterProcess.handleStateTransitionMessages();

        verifyNoMoreInteractions(eventQueue);
        verify(paymentStateTransitionQueue).offer(paymentStateTransition);
    }

    @Test
    public void shouldPutPaymentTransitionBackOnQueueIfEventEmitFails() throws Exception {
        ChargeEventEntity chargeEventEntity = mock(ChargeEventEntity.class);
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(100L, PaymentEvent.class);
        when(paymentStateTransitionQueue.poll()).thenReturn(paymentStateTransition);
        when(chargeEventDao.findById(ChargeEventEntity.class, 100L)).thenReturn(Optional.of(chargeEventEntity));
        doThrow(QueueException.class).when(eventQueue).emitEvent(any());

        paymentStateTransitionEmitterProcess.handleStateTransitionMessages();

        verifyNoMoreInteractions(eventQueue);
        verify(paymentStateTransitionQueue).offer(paymentStateTransition);
    }
}
