package uk.gov.pay.connector.payout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.payout.PayoutReconcileMessage;
import uk.gov.pay.connector.queue.payout.PayoutReconcileQueue;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PayoutReconcileProcessTest {

    @Mock
    PayoutReconcileQueue payoutReconcileQueue;
    @Mock
    PayoutReconcileMessage payoutReconcileMessage;

    PayoutReconcileProcess payoutReconcileProcess;

    @Before
    public void setUp() throws Exception {
        List<PayoutReconcileMessage> messages = Arrays.asList(payoutReconcileMessage);
        when(payoutReconcileQueue.retrievePayoutMessages()).thenReturn(messages);

        payoutReconcileProcess = new PayoutReconcileProcess(payoutReconcileQueue);
    }

    @Test
    public void shouldMarkMessageAsProcessedIfPayoutIsProcessedSuccessfully() throws QueueException {
        payoutReconcileProcess.processPayouts();

        verify(payoutReconcileQueue).markMessageAsProcessed(payoutReconcileMessage);
    }
}
