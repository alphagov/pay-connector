package uk.gov.pay.connector.payout;

import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Transfer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;
import uk.gov.pay.connector.queue.QueueMessage;
import uk.gov.pay.connector.queue.payout.Payout;
import uk.gov.pay.connector.queue.payout.PayoutReconcileMessage;
import uk.gov.pay.connector.queue.payout.PayoutReconcileQueue;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;

@RunWith(MockitoJUnitRunner.class)
public class PayoutReconcileProcessTest {

    @Mock
    private PayoutReconcileQueue payoutReconcileQueue;

    @Mock
    private StripeClientWrapper stripeClientWrapper;

    @Mock
    private StripeGatewayConfig stripeGatewayConfig;

    @Mock
    private StripeAuthTokens stripeAuthTokens;

    @Mock
    private GatewayAccountDao gatewayAccountDao;

    @InjectMocks
    private PayoutReconcileProcess payoutReconcileProcess;

    private final String stripeAccountId = "acct_2RDpWRLXEC2XwBWp";
    private final String stripeApiKey = "a-fake-api-key";

    @Before
    public void setUp() {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withType(GatewayAccountEntity.Type.TEST).build();
        when(gatewayAccountDao.findByCredentialsKeyValue(StripeCredentials.STRIPE_ACCOUNT_ID_KEY, stripeAccountId))
                .thenReturn(Optional.of(gatewayAccountEntity));
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        when(stripeAuthTokens.getTest()).thenReturn(stripeApiKey);
    }

    @Test
    public void shouldMarkMessageAsProcessedIfPayoutIsProcessedSuccessfully() throws Exception {
        String payoutId = "po_123dv3RPEC2XwBWpqiQfnJGQ";
        Payout payout = new Payout(payoutId, stripeAccountId);
        QueueMessage mockQueueMessage = mock(QueueMessage.class);
        PayoutReconcileMessage payoutReconcileMessage = PayoutReconcileMessage.of(payout, mockQueueMessage);
        when(payoutReconcileQueue.retrievePayoutMessages()).thenReturn(List.of(payoutReconcileMessage));

        BalanceTransaction paymentBalanceTransaction = mock(BalanceTransaction.class);
        Charge paymentSource = mock(Charge.class);
        Transfer paymentTransferSource = mock(Transfer.class);
        when(paymentBalanceTransaction.getType()).thenReturn("payment");
        when(paymentBalanceTransaction.getSourceObject()).thenReturn(paymentSource);
        when(paymentSource.getSourceTransferObject()).thenReturn(paymentTransferSource);
        when(paymentTransferSource.getTransferGroup()).thenReturn("payment-id");

        BalanceTransaction refundBalanceTransaction = mock(BalanceTransaction.class);
        when(refundBalanceTransaction.getType()).thenReturn("transfer");
        when(refundBalanceTransaction.getSource()).thenReturn("refund-transfer-id");

        when(stripeClientWrapper.getBalanceTransactionsForPayout(eq(payoutId), eq(stripeAccountId), eq(stripeApiKey)))
                .thenReturn(List.of(paymentBalanceTransaction, refundBalanceTransaction));

        payoutReconcileProcess.processPayouts();

        verify(payoutReconcileQueue).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    public void shouldNotMarkMessageAsSuccessfullyProcessedIfNoPaymentsOrRefundsFound() throws Exception {
        String payoutId = "po_123dv3RPEC2XwBWpqiQfnJGQ";
        Payout payout = new Payout(payoutId, stripeAccountId);
        QueueMessage mockQueueMessage = mock(QueueMessage.class);
        PayoutReconcileMessage payoutReconcileMessage = PayoutReconcileMessage.of(payout, mockQueueMessage);
        when(payoutReconcileQueue.retrievePayoutMessages()).thenReturn(List.of(payoutReconcileMessage));

        when(stripeClientWrapper.getBalanceTransactionsForPayout(eq(payoutId), eq(stripeAccountId), eq(stripeApiKey)))
                .thenReturn(List.of());

        payoutReconcileProcess.processPayouts();

        verify(payoutReconcileQueue, never()).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    public void shouldNotMarkMessageAsSuccessfullyProcessedIfExceptionThrown() throws Exception {
        String payoutId = "po_123dv3RPEC2XwBWpqiQfnJGQ";
        Payout payout = new Payout(payoutId, "non-existent-connect-account-id");
        QueueMessage mockQueueMessage = mock(QueueMessage.class);
        PayoutReconcileMessage payoutReconcileMessage = PayoutReconcileMessage.of(payout, mockQueueMessage);
        when(payoutReconcileQueue.retrievePayoutMessages()).thenReturn(List.of(payoutReconcileMessage));

        payoutReconcileProcess.processPayouts();

        verify(payoutReconcileQueue, never()).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }
}
