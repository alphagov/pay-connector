package uk.gov.pay.connector.payout;

import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Transfer;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.charge.PaymentIncludedInPayout;
import uk.gov.pay.connector.events.model.refund.RefundIncludedInPayout;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;
import uk.gov.pay.connector.queue.QueueException;
import uk.gov.pay.connector.queue.QueueMessage;
import uk.gov.pay.connector.queue.payout.Payout;
import uk.gov.pay.connector.queue.payout.PayoutReconcileMessage;
import uk.gov.pay.connector.queue.payout.PayoutReconcileQueue;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.stripe.request.StripeTransferMetadata.GOVUK_PAY_TRANSACTION_EXTERNAL_ID;
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
    private ConnectorConfiguration connectorConfiguration;

    @Mock
    private StripeAuthTokens stripeAuthTokens;

    @Mock
    private GatewayAccountDao gatewayAccountDao;
    
    @Mock
    private EventService eventService;

    @InjectMocks
    private PayoutReconcileProcess payoutReconcileProcess;

    private final String stripeAccountId = "acct_2RDpWRLXEC2XwBWp";
    private final String stripeApiKey = "a-fake-api-key";
    private final String payoutId = "po_123dv3RPEC2XwBWpqiQfnJGQ";
    private final ZonedDateTime payoutCreatedDate = ZonedDateTime.parse("2020-05-01T10:30:00.000Z");
    private final String paymentExternalId = "payment-id";
    private final String refundExternalId = "refund-id";

    @Before
    public void setUp() throws Exception {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withType(GatewayAccountEntity.Type.TEST).build();
        when(gatewayAccountDao.findByCredentialsKeyValue(StripeCredentials.STRIPE_ACCOUNT_ID_KEY, stripeAccountId))
                .thenReturn(Optional.of(gatewayAccountEntity));
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        when(stripeAuthTokens.getTest()).thenReturn(stripeApiKey);

        setupMockBalanceTransactions();
    }

    @Test
    public void shouldEmitEventsForMessageAndMarkAsProcessed() throws Exception {
        when(connectorConfiguration.getEmitPayoutEvents()).thenReturn(true);

        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage(stripeAccountId);

        payoutReconcileProcess.processPayouts();
        
        var paymentEvent = new PaymentIncludedInPayout(paymentExternalId, payoutId, payoutCreatedDate);
        var refundEvent = new RefundIncludedInPayout(refundExternalId, payoutId, payoutCreatedDate);
        
        verify(eventService).emitEvent(eq(paymentEvent), eq(false));
        verify(eventService).emitEvent(eq(refundEvent), eq(false));
        verify(payoutReconcileQueue).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    public void shouldNotEmitEventsIfConnectorConfigurationDisabled() throws Exception {
        when(connectorConfiguration.getEmitPayoutEvents()).thenReturn(false);

        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage(stripeAccountId);

        payoutReconcileProcess.processPayouts();
        
        verify(eventService, never()).emitEvent(any(), anyBoolean());
        verify(payoutReconcileQueue).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    public void shouldNotMarkMessageAsSuccessfullyProcessedIfNoPaymentsOrRefundsFound() throws Exception {
        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage(stripeAccountId);

        when(stripeClientWrapper.getBalanceTransactionsForPayout(eq(payoutId), eq(stripeAccountId), eq(stripeApiKey)))
                .thenReturn(List.of());

        payoutReconcileProcess.processPayouts();

        verify(payoutReconcileQueue, never()).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    public void shouldNotMarkMessageAsSuccessfullyProcessedIfGatewayAccountNotFound() throws Exception {
        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage("non-existent-connect-account-id");

        payoutReconcileProcess.processPayouts();

        verify(payoutReconcileQueue, never()).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    public void shouldNotMarkMessageAsSuccessfullyProcessedIfEventEmissionFails() throws Exception {
        when(connectorConfiguration.getEmitPayoutEvents()).thenReturn(true);

        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage(stripeAccountId);

        doThrow(new QueueException()).when(eventService).emitEvent(any(), anyBoolean());

        payoutReconcileProcess.processPayouts();

        verify(payoutReconcileQueue, never()).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    private PayoutReconcileMessage setupQueueMessage(String stripeAccountId) throws QueueException {
        Payout payout = new Payout(payoutId, stripeAccountId, payoutCreatedDate);
        QueueMessage mockQueueMessage = mock(QueueMessage.class);
        PayoutReconcileMessage payoutReconcileMessage = PayoutReconcileMessage.of(payout, mockQueueMessage);
        when(payoutReconcileQueue.retrievePayoutMessages()).thenReturn(List.of(payoutReconcileMessage));
        return payoutReconcileMessage;
    }

    private void setupMockBalanceTransactions() throws StripeException {
        BalanceTransaction paymentBalanceTransaction = mock(BalanceTransaction.class);
        Charge paymentSource = mock(Charge.class);
        Transfer paymentTransferSource = mock(Transfer.class);
        when(paymentBalanceTransaction.getType()).thenReturn("payment");
        when(paymentBalanceTransaction.getSourceObject()).thenReturn(paymentSource);
        when(paymentSource.getSourceTransferObject()).thenReturn(paymentTransferSource);
        when(paymentTransferSource.getMetadata()).thenReturn(Map.of(GOVUK_PAY_TRANSACTION_EXTERNAL_ID, paymentExternalId));

        BalanceTransaction refundBalanceTransaction = mock(BalanceTransaction.class);
        Transfer refundTransferSource = mock(Transfer.class);
        when(refundBalanceTransaction.getType()).thenReturn("transfer");
        when(refundBalanceTransaction.getSourceObject()).thenReturn(refundTransferSource);
        when(refundTransferSource.getMetadata()).thenReturn(Map.of(GOVUK_PAY_TRANSACTION_EXTERNAL_ID, refundExternalId));

        when(stripeClientWrapper.getBalanceTransactionsForPayout(eq(payoutId), eq(stripeAccountId), eq(stripeApiKey)))
                .thenReturn(List.of(paymentBalanceTransaction, refundBalanceTransaction));
    }
}
