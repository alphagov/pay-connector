package uk.gov.pay.connector.payout;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Transfer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.charge.PaymentIncludedInPayout;
import uk.gov.pay.connector.events.model.payout.PayoutCreated;
import uk.gov.pay.connector.events.model.refund.RefundIncludedInPayout;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
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

    @Mock
    private PayoutEmitterService payoutEmitterService;

    @Mock
    private Appender<ILoggingEvent> logAppender;

    @InjectMocks
    private PayoutReconcileProcess payoutReconcileProcess;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

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

        Logger errorLogger = (Logger) LoggerFactory.getLogger(PayoutReconcileProcess.class);
        errorLogger.setLevel(Level.ERROR);
        errorLogger.addAppender(logAppender);
    }

    @Test
    public void shouldEmitEventsForMessageAndMarkAsProcessed() throws Exception {
        when(connectorConfiguration.getEmitPayoutEvents()).thenReturn(true);

        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage(stripeAccountId);

        payoutReconcileProcess.processPayouts();

        var paymentEvent = new PaymentIncludedInPayout(paymentExternalId, payoutId, payoutCreatedDate);
        var refundEvent = new RefundIncludedInPayout(refundExternalId, payoutId, payoutCreatedDate);
        StripePayout stripePayout = new StripePayout("po_123", 1213L, 1589395533L,
                1589395500L, "pending", "card", "statement_desc");

        verify(eventService).emitEvent(eq(paymentEvent), eq(false));
        verify(eventService).emitEvent(eq(refundEvent), eq(false));
        verify(payoutEmitterService).emitPayoutEvent(PayoutCreated.class, stripePayout.getCreated(),
                stripeAccountId, stripePayout);

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

        verify(logAppender).doAppend(loggingEventArgumentCaptor.capture());
        assertThat(loggingEventArgumentCaptor.getValue().getFormattedMessage(), containsString("Gateway account with Stripe connect account ID [non-existent-connect-account-id] not found"));

        verify(payoutReconcileQueue, never()).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    public void shouldNotMarkMessageAsSuccessfullyProcessedIfEventEmissionFails() throws Exception {
        when(connectorConfiguration.getEmitPayoutEvents()).thenReturn(true);

        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage(stripeAccountId);

        doThrow(new QueueException()).when(eventService).emitEvent(any(), anyBoolean());

        payoutReconcileProcess.processPayouts();

        verify(logAppender).doAppend(loggingEventArgumentCaptor.capture());
        assertThat(loggingEventArgumentCaptor.getValue().getFormattedMessage(), containsString("Error sending PAYMENT_INCLUDED_IN_PAYOUT event"));

        verify(payoutReconcileQueue, never()).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    public void shouldNotMarkMessageAsSuccessfullyProcessedIfStripeTransferMetadataMissing() throws Exception {
        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage(stripeAccountId);

        BalanceTransaction refundBalanceTransaction = mock(BalanceTransaction.class);
        Transfer refundTransferSource = mock(Transfer.class);
        when(refundBalanceTransaction.getType()).thenReturn("transfer");
        when(refundBalanceTransaction.getSourceObject()).thenReturn(refundTransferSource);
        when(refundTransferSource.getMetadata()).thenReturn(Map.of());

        when(stripeClientWrapper.getBalanceTransactionsForPayout(eq(payoutId), eq(stripeAccountId), eq(stripeApiKey)))
                .thenReturn(List.of(refundBalanceTransaction));

        payoutReconcileProcess.processPayouts();

        verify(logAppender).doAppend(loggingEventArgumentCaptor.capture());
        assertThat(loggingEventArgumentCaptor.getValue().getFormattedMessage(), containsString("Transaction external ID missing in metadata"));

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

        BalanceTransaction payoutBalanceTransaction = mock(BalanceTransaction.class);
        com.stripe.model.Payout payoutSource = mock(com.stripe.model.Payout.class);
        when(payoutSource.getId()).thenReturn("po_123");
        when(payoutSource.getAmount()).thenReturn(1213L);
        when(payoutSource.getArrivalDate()).thenReturn(1589395533L);
        when(payoutSource.getCreated()).thenReturn(1589395500L);
        when(payoutSource.getStatus()).thenReturn("pending");
        when(payoutSource.getType()).thenReturn("card");
        when(payoutSource.getStatementDescriptor()).thenReturn("statement_desc");
        when(payoutBalanceTransaction.getType()).thenReturn("payout");
        when(payoutBalanceTransaction.getSourceObject()).thenReturn(payoutSource);

        when(stripeClientWrapper.getBalanceTransactionsForPayout(eq(payoutId), eq(stripeAccountId), eq(stripeApiKey)))
                .thenReturn(List.of(paymentBalanceTransaction, refundBalanceTransaction, payoutBalanceTransaction));
    }
}
