package uk.gov.pay.connector.events;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.queue.PaymentStateTransition;
import uk.gov.pay.connector.queue.PaymentStateTransitionQueue;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PaymentStateTransitionEmitterProcessTest {

    @Mock
    PaymentStateTransitionQueue paymentStateTransitionQueue;

    @Mock
    EventQueue eventQueue;

    @InjectMocks
    PaymentStateTransitionEmitterProcess paymentStateTransitionEmitterProcess;

    @Test
    public void shouldEmitPaymentEventGivenStateTransitionMessageOnQueue() throws Exception {
        PaymentStateTransition paymentStateTransition = new PaymentStateTransition(100L);
        when(paymentStateTransitionQueue.poll()).thenReturn(paymentStateTransition);

        paymentStateTransitionEmitterProcess.handleStateTransitionMessages();

        verify(eventQueue).emitEvent(any());
    }
}
