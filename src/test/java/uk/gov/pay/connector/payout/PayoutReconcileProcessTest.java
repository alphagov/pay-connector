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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.charge.PaymentIncludedInPayout;
import uk.gov.pay.connector.events.model.dispute.DisputeIncludedInPayout;
import uk.gov.pay.connector.events.model.payout.PayoutCreated;
import uk.gov.pay.connector.events.model.payout.PayoutEvent;
import uk.gov.pay.connector.events.model.payout.PayoutFailed;
import uk.gov.pay.connector.events.model.payout.PayoutPaid;
import uk.gov.pay.connector.events.model.refund.RefundIncludedInPayout;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClient;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.model.StripeCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.queue.payout.Payout;
import uk.gov.pay.connector.queue.payout.PayoutReconcileMessage;
import uk.gov.pay.connector.queue.payout.PayoutReconcileQueue;
import uk.gov.service.payments.commons.queue.exception.QueueException;
import uk.gov.service.payments.commons.queue.model.QueueMessage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.stripe.request.StripeTransferMetadata.GOVUK_PAY_TRANSACTION_EXTERNAL_ID;
import static uk.gov.pay.connector.gateway.stripe.request.StripeTransferMetadata.REASON_KEY;
import static uk.gov.pay.connector.gateway.stripe.request.StripeTransferMetadataReason.TRANSFER_DISPUTE_AMOUNT;
import static uk.gov.pay.connector.gateway.stripe.request.StripeTransferMetadataReason.TRANSFER_FEE_AMOUNT_FOR_FAILED_PAYMENT;
import static uk.gov.pay.connector.gateway.stripe.request.StripeTransferMetadataReason.TRANSFER_REFUND_AMOUNT;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;

@ExtendWith(MockitoExtension.class)
class PayoutReconcileProcessTest {

    @Mock
    private PayoutReconcileQueue payoutReconcileQueue;

    @Mock
    private StripeSdkClient stripeSDKClient;

    @Mock
    private ConnectorConfiguration connectorConfiguration;
    
    @Mock
    private GatewayAccountCredentialsService gatewayAccountCredentialsService;

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
    private final Instant payoutCreatedDate = Instant.parse("2020-05-01T10:30:00.000Z");
    private final String paymentExternalId = "payment-id";
    private final String refundExternalId = "refund-id";
    private final String failedPaymentWithFeeExternalId = "failed-payment-with-fee-id";
    private final String disputeExternalId = "dispute-id";

    @BeforeEach
    void setUp() throws Exception {
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withType(GatewayAccountType.TEST)
                .build();

        when(gatewayAccountCredentialsService.findStripeGatewayAccountForCredentialKeyAndValue(StripeCredentials.STRIPE_ACCOUNT_ID_KEY, stripeAccountId))
                .thenReturn(gatewayAccountEntity);

        setupMockBalanceTransactions("pending");

        Logger errorLogger = (Logger) LoggerFactory.getLogger(PayoutReconcileProcess.class);
        errorLogger.setLevel(Level.ERROR);
        errorLogger.addAppender(logAppender);
    }

    @Test
    void shouldEmitEventsForMessageAndMarkAsProcessed() throws Exception {
        var paymentEvent = new PaymentIncludedInPayout(paymentExternalId, payoutId, payoutCreatedDate);
        var refundEvent = new RefundIncludedInPayout(refundExternalId, payoutId, payoutCreatedDate);
        var feeCollectionEvent = new PaymentIncludedInPayout(failedPaymentWithFeeExternalId, payoutId, payoutCreatedDate);
        var disputeEvent = new DisputeIncludedInPayout(disputeExternalId, payoutId, payoutCreatedDate);
        var stripePayout = new StripePayout("po_123", 1213L, 1589395533L,
                1589395500L, "pending", "card", "statement_desc");
        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage();
        when(connectorConfiguration.getEmitPayoutEvents()).thenReturn(true);

        payoutReconcileProcess.processPayouts();

        verify(eventService).emitEvent(paymentEvent, false);
        verify(eventService).emitEvent(refundEvent, false);
        verify(eventService).emitEvent(feeCollectionEvent, false);
        verify(eventService).emitEvent(disputeEvent, false);
        verifyNoMoreInteractions(eventService);
        verify(payoutEmitterService).emitPayoutEvent(PayoutCreated.class, stripePayout.getCreated(), stripeAccountId, stripePayout);
        verify(payoutReconcileQueue).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    void shouldEmitAdditionalPayoutPaidEventIfPayoutStatusIsPaid() throws Exception {
        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage();
        ArgumentCaptor<Class<? extends PayoutEvent>> captor = ArgumentCaptor.forClass(Class.class);
        setupMockBalanceTransactions("paid");
        when(connectorConfiguration.getEmitPayoutEvents()).thenReturn(true);

        payoutReconcileProcess.processPayouts();

        verify(payoutEmitterService, times(2)).emitPayoutEvent(
                captor.capture(), any(), any(), any());

        assertThat(captor.getAllValues().get(0), is(PayoutCreated.class));
        assertThat(captor.getAllValues().get(1), is(PayoutPaid.class));

        verify(payoutReconcileQueue).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    void shouldEmitAdditionalPayoutFailedEventIfPayoutStatusIsFailed() throws Exception {
        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage();
        ArgumentCaptor<Class<? extends PayoutEvent>> captor = ArgumentCaptor.forClass(Class.class);
        ArgumentCaptor<StripePayout> captorForStripePayout = ArgumentCaptor.forClass(StripePayout.class);
        setupMockBalanceTransactions("failed");
        when(connectorConfiguration.getEmitPayoutEvents()).thenReturn(true);

        payoutReconcileProcess.processPayouts();

        verify(payoutEmitterService, times(2)).emitPayoutEvent(
                captor.capture(), any(), any(), captorForStripePayout.capture());

        StripePayout stripePayoutForFailedEvent = captorForStripePayout.getAllValues().get(1);

        assertThat(captor.getAllValues().get(0), is(PayoutCreated.class));
        assertThat(captor.getAllValues().get(1), is(PayoutFailed.class));

        assertThat(stripePayoutForFailedEvent.getFailureCode(), is("account_closed"));
        assertThat(stripePayoutForFailedEvent.getFailureMessage(), is("The bank account has been closed"));
        assertThat(stripePayoutForFailedEvent.getFailureBalanceTransaction(), is("ba_1GkZtqDv3CZEaFO2CQhLrluk"));

        verify(payoutReconcileQueue).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    void shouldNotEmitEventsIfConnectorConfigurationDisabled() throws Exception {
        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage();
        when(connectorConfiguration.getEmitPayoutEvents()).thenReturn(false);

        payoutReconcileProcess.processPayouts();

        verify(eventService, never()).emitEvent(any(), anyBoolean());
        verify(payoutReconcileQueue).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    void shouldNotMarkMessageAsSuccessfullyProcessedIfNoPaymentsOrRefundsFound() throws Exception {
        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage();
        when(stripeSDKClient.getBalanceTransactionsForPayout(payoutId, stripeAccountId, false))
                .thenReturn(List.of());

        payoutReconcileProcess.processPayouts();

        verify(payoutReconcileQueue, never()).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    void shouldNotMarkMessageAsSuccessfullyProcessedIfEventEmissionFails() throws Exception {
        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage();
        when(connectorConfiguration.getEmitPayoutEvents()).thenReturn(true);

        doThrow(new QueueException()).when(eventService).emitEvent(any(), anyBoolean());

        payoutReconcileProcess.processPayouts();

        verify(logAppender).doAppend(loggingEventArgumentCaptor.capture());
        assertThat(loggingEventArgumentCaptor.getValue().getFormattedMessage(), containsString("Error sending PAYMENT_INCLUDED_IN_PAYOUT event"));
        verify(payoutReconcileQueue, never()).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    void shouldNotMarkMessageAsSuccessfullyProcessedIfStripeTransferTransactionIdMetadataMissing() throws Exception {
        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage();
        BalanceTransaction refundBalanceTransaction = mock(BalanceTransaction.class);
        Transfer refundTransferSource = mock(Transfer.class);
        when(refundBalanceTransaction.getType()).thenReturn("transfer");
        when(refundBalanceTransaction.getSourceObject()).thenReturn(refundTransferSource);
        when(refundTransferSource.getMetadata()).thenReturn(Map.of());
        when(stripeSDKClient.getBalanceTransactionsForPayout(payoutId, stripeAccountId, false))
                .thenReturn(List.of(refundBalanceTransaction));

        payoutReconcileProcess.processPayouts();

        verify(logAppender).doAppend(loggingEventArgumentCaptor.capture());
        assertThat(loggingEventArgumentCaptor.getValue().getFormattedMessage(), containsString("Transaction external ID missing in metadata"));
        verify(payoutReconcileQueue, never()).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    void shouldNotMarkMessageAsSuccessfullyProcessedIfTransferMetadataReasonNotRecognised() throws Exception {
        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage();
        BalanceTransaction balanceTransaction = mock(BalanceTransaction.class);
        Transfer transfer = mock(Transfer.class);
        String balanceTransactionId = "balance-transaction-id";
        when(balanceTransaction.getId()).thenReturn(balanceTransactionId);
        when(balanceTransaction.getType()).thenReturn("transfer");
        when(balanceTransaction.getSourceObject()).thenReturn(transfer);
        when(transfer.getMetadata()).thenReturn(Map.of(
                GOVUK_PAY_TRANSACTION_EXTERNAL_ID, "a_transaction_id",
                REASON_KEY, "some_unknown_reason"
        ));
        when(stripeSDKClient.getBalanceTransactionsForPayout(payoutId, stripeAccountId, false))
                .thenReturn(List.of(balanceTransaction));

        payoutReconcileProcess.processPayouts();

        verify(logAppender).doAppend(loggingEventArgumentCaptor.capture());
        assertThat(loggingEventArgumentCaptor.getValue().getFormattedMessage(), containsString(format("Stripe balance transaction %s has unexpected 'reason' in metadata", balanceTransactionId)));
        verify(payoutReconcileQueue, never()).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    @Test
    void shouldReconcileTransferMissingReasonMetadataAsRefund() throws Exception {
        PayoutReconcileMessage payoutReconcileMessage = setupQueueMessage();
        BalanceTransaction refundBalanceTransaction = mock(BalanceTransaction.class);
        Transfer refundTransferSource = mock(Transfer.class);
        when(refundBalanceTransaction.getType()).thenReturn("transfer");
        when(refundBalanceTransaction.getSourceObject()).thenReturn(refundTransferSource);
        when(refundTransferSource.getMetadata()).thenReturn(Map.of(GOVUK_PAY_TRANSACTION_EXTERNAL_ID, refundExternalId));
        when(stripeSDKClient.getBalanceTransactionsForPayout(payoutId, stripeAccountId, false))
                .thenReturn(List.of(refundBalanceTransaction));
        when(connectorConfiguration.getEmitPayoutEvents()).thenReturn(true);

        payoutReconcileProcess.processPayouts();

        var refundEvent = new RefundIncludedInPayout(refundExternalId, payoutId, payoutCreatedDate);
        verify(eventService).emitEvent(refundEvent, false);
        verifyNoMoreInteractions(eventService);
        verify(payoutReconcileQueue).markMessageAsProcessed(payoutReconcileMessage.getQueueMessage());
    }

    private PayoutReconcileMessage setupQueueMessage() throws QueueException {
        Payout payout = new Payout(payoutId, stripeAccountId, payoutCreatedDate);
        QueueMessage mockQueueMessage = mock(QueueMessage.class);
        PayoutReconcileMessage payoutReconcileMessage = PayoutReconcileMessage.of(payout, mockQueueMessage);
        when(payoutReconcileQueue.retrievePayoutMessages()).thenReturn(List.of(payoutReconcileMessage));
        return payoutReconcileMessage;
    }

    private void setupMockBalanceTransactions(String payoutStatus) throws StripeException {
        BalanceTransaction paymentBalanceTransaction = mock(BalanceTransaction.class);
        Charge paymentSource = mock(Charge.class);
        Transfer paymentTransferSource = mock(Transfer.class);
        lenient().when(paymentBalanceTransaction.getType()).thenReturn("payment");
        lenient().when(paymentBalanceTransaction.getSourceObject()).thenReturn(paymentSource);
        lenient().when(paymentSource.getSourceTransferObject()).thenReturn(paymentTransferSource);
        lenient().when(paymentTransferSource.getMetadata()).thenReturn(Map.of(GOVUK_PAY_TRANSACTION_EXTERNAL_ID, paymentExternalId));

        BalanceTransaction refundBalanceTransaction = mock(BalanceTransaction.class);
        Transfer refundTransferSource = mock(Transfer.class);
        lenient().when(refundBalanceTransaction.getType()).thenReturn("transfer");
        lenient().when(refundBalanceTransaction.getSourceObject()).thenReturn(refundTransferSource);
        lenient().when(refundTransferSource.getMetadata()).thenReturn(Map.of(
                GOVUK_PAY_TRANSACTION_EXTERNAL_ID, refundExternalId,
                REASON_KEY, TRANSFER_REFUND_AMOUNT.toString()
        ));

        BalanceTransaction feeBalanceTransaction = mock(BalanceTransaction.class);
        Transfer feeTransferSource = mock(Transfer.class);
        lenient().when(feeBalanceTransaction.getType()).thenReturn("transfer");
        lenient().when(feeBalanceTransaction.getSourceObject()).thenReturn(feeTransferSource);
        lenient().when(feeTransferSource.getMetadata()).thenReturn(Map.of(
                GOVUK_PAY_TRANSACTION_EXTERNAL_ID, failedPaymentWithFeeExternalId,
                REASON_KEY, TRANSFER_FEE_AMOUNT_FOR_FAILED_PAYMENT.toString()
        ));

        BalanceTransaction disputeBalanceTransaction = mock(BalanceTransaction.class);
        Transfer disputeTransferSource = mock(Transfer.class);
        lenient().when(disputeBalanceTransaction.getType()).thenReturn("transfer");
        lenient().when(disputeBalanceTransaction.getSourceObject()).thenReturn(disputeTransferSource);
        lenient().when(disputeTransferSource.getMetadata()).thenReturn(Map.of(
                GOVUK_PAY_TRANSACTION_EXTERNAL_ID, disputeExternalId,
                REASON_KEY, TRANSFER_DISPUTE_AMOUNT.toString()
        ));

        BalanceTransaction payoutBalanceTransaction = mock(BalanceTransaction.class);
        com.stripe.model.Payout payoutSource = mock(com.stripe.model.Payout.class);
        lenient().when(payoutSource.getId()).thenReturn("po_123");
        lenient().when(payoutSource.getAmount()).thenReturn(1213L);
        lenient().when(payoutSource.getArrivalDate()).thenReturn(1589395533L);
        lenient().when(payoutSource.getCreated()).thenReturn(1589395500L);
        lenient().when(payoutSource.getStatus()).thenReturn(payoutStatus);
        lenient().when(payoutSource.getType()).thenReturn("card");
        lenient().when(payoutSource.getStatementDescriptor()).thenReturn("statement_desc");

        if ("failed".equals(payoutStatus)) {
            when(payoutSource.getFailureCode()).thenReturn("account_closed");
            when(payoutSource.getFailureMessage()).thenReturn("The bank account has been closed");
            when(payoutSource.getFailureBalanceTransaction()).thenReturn("ba_1GkZtqDv3CZEaFO2CQhLrluk");
        }

        lenient().when(payoutBalanceTransaction.getType()).thenReturn("payout");
        lenient().when(payoutBalanceTransaction.getSourceObject()).thenReturn(payoutSource);

        List<BalanceTransaction> balanceTransactions = List.of(
                paymentBalanceTransaction,
                refundBalanceTransaction,
                feeBalanceTransaction,
                disputeBalanceTransaction,
                payoutBalanceTransaction);
        when(stripeSDKClient.getBalanceTransactionsForPayout(payoutId, stripeAccountId, false))
                .thenReturn(balanceTransactions);
    }
}
