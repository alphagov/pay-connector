package uk.gov.pay.connector.queue.tasks;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.client.ledger.model.CardDetails;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeEvidenceSubmittedEventDetails;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeLostEventDetails;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.events.model.charge.PaymentDisputed;
import uk.gov.pay.connector.events.model.charge.RefundAvailabilityUpdated;
import uk.gov.pay.connector.events.model.dispute.DisputeCreated;
import uk.gov.pay.connector.events.model.dispute.DisputeEvidenceSubmitted;
import uk.gov.pay.connector.events.model.dispute.DisputeLost;
import uk.gov.pay.connector.events.model.dispute.DisputeWon;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gateway.stripe.json.StripeDisputeData;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountCredentialsNotFoundException;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.queue.tasks.handlers.StripeWebhookTaskHandler;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_CHARGE_DISPUTE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_CHARGE_DISPUTE_LOST_WITH_MULTIPLE_BALANCE_TRANSACTIONS;

@ExtendWith(MockitoExtension.class)
public class StripeWebhookTaskHandlerTest {

    @Mock
    private Appender<ILoggingEvent> mockLogAppender;
    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private EventService eventService;
    @Mock 
    private ChargeService chargeService;
    @Mock
    private StripePaymentProvider stripePaymentProvider;
    @Mock
    private GatewayAccountService gatewayAccountService;
    @Mock
    private GatewayAccountCredentialsService gatewayAccountCredentialsService;
    @Mock
    private ConnectorConfiguration configuration;
    @Mock
    private StripeGatewayConfig stripeGatewayConfig;

    private final String fixedClockDateTime = "2020-01-01T10:10:10.100Z";
    private final Clock clock = Clock.fixed(Instant.parse(fixedClockDateTime), ZoneOffset.UTC);

    private StripeWebhookTaskHandler stripeWebhookTaskHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String PLACEHOLDER_TYPE = "{{type}}";
    private final String PLACEHOLDER_STATUS = "{{status}}";

    long gatewayAccountId = 1000L;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(StripeWebhookTaskHandler.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(mockLogAppender);
        when(configuration.getStripeConfig()).thenReturn(stripeGatewayConfig);
        stripeWebhookTaskHandler = new StripeWebhookTaskHandler(ledgerService, chargeService, eventService, stripePaymentProvider,
                gatewayAccountService, gatewayAccountCredentialsService, configuration, clock);
    }

    @Test
    void shouldReadPayloadProperlyWhenDisputeCreatedNotification_andNotSubmitTestEvidence_whenAccountIsLive() throws JsonProcessingException, GatewayException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.created", "needs_response", true);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        stripeWebhookTaskHandler.process(stripeNotification);

        String resourceExternalId = RandomIdGenerator.idFromExternalId(stripeDisputeData.getId());
        var disputeCreated = DisputeCreated.from(resourceExternalId, stripeDisputeData, transaction,
                stripeDisputeData.getDisputeCreated().toInstant());
        var paymentDisputed = PaymentDisputed.from(transaction, stripeDisputeData.getDisputeCreated().toInstant());
        var refundAvailabilityUpdated = RefundAvailabilityUpdated.from(
                transaction, ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE, Instant.parse(fixedClockDateTime));

        verify(eventService).emitEvent(disputeCreated);
        verify(eventService).emitEvent(paymentDisputed);
        verify(eventService).emitEvent(refundAvailabilityUpdated);

        verify(stripePaymentProvider, never()).submitTestDisputeEvidence(anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotEmitEventsAndShouldLogWhenDisputeCreatedWithWarningNeedsResponse() throws Exception {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.created", "warning_needs_response", true);
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        stripeWebhookTaskHandler.process(stripeNotification);

        verify(stripePaymentProvider, never()).submitTestDisputeEvidence(anyString(), anyString(), anyString());

        verify(mockLogAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Skipping dispute notification: [status: warning_needs_response, type: charge.dispute.created, payment_intent: pi_1111111111, reason: general]";

        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));

        verifyNoInteractions(eventService);
    }

    @Test
    void shouldEmitEventsWhenDisputeWon() throws Exception {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        Charge charge = Charge.from(transaction);
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.closed", "won", true);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
        RefundAvailabilityUpdated refundAvailabilityUpdated = mock(RefundAvailabilityUpdated.class);

        String resourceExternalId = RandomIdGenerator.idFromExternalId(stripeDisputeData.getId());
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        when(chargeService.createRefundAvailabilityUpdatedEvent(charge, stripeNotification.getCreated().toInstant())).thenReturn(refundAvailabilityUpdated);
        stripeWebhookTaskHandler.process(stripeNotification);
        ArgumentCaptor<DisputeWon> argumentCaptor = ArgumentCaptor.forClass(DisputeWon.class);

        var disputeWon = DisputeWon.from(resourceExternalId, stripeNotification.getCreated().toInstant(), transaction);
        verify(eventService).emitEvent(disputeWon);
        verify(eventService).emitEvent(refundAvailabilityUpdated);
    }

    @Test
    void shouldEmitEventsWhenDisputeWon_withAdjustedEventDateForTestPayment() throws Exception {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        Charge charge = Charge.from(transaction);
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.closed", "won", false);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
        RefundAvailabilityUpdated refundAvailabilityUpdated = mock(RefundAvailabilityUpdated.class);

        String resourceExternalId = RandomIdGenerator.idFromExternalId(stripeDisputeData.getId());
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        when(chargeService.createRefundAvailabilityUpdatedEvent(charge, stripeNotification.getCreated().plusSeconds(1).toInstant())).thenReturn(refundAvailabilityUpdated);
        stripeWebhookTaskHandler.process(stripeNotification);
        ArgumentCaptor<DisputeWon> argumentCaptor = ArgumentCaptor.forClass(DisputeWon.class);

        var disputeWon = DisputeWon.from(resourceExternalId, stripeNotification.getCreated().plusSeconds(1).toInstant(), transaction);
        verify(eventService).emitEvent(disputeWon);
        verify(eventService).emitEvent(refundAvailabilityUpdated);
    }

    @Test
    void shouldMakeTransferInStripeAndEmitEventForLostDispute_whenDisputeCreatedAfterRechargeEnabledDate() throws Exception {
        LedgerTransaction transaction = buildTransaction(true);
        Charge charge = Charge.from(transaction);
        GatewayAccountEntity gatewayAccount = aGatewayAccountEntity().withId(gatewayAccountId).build();
        GatewayAccountCredentialsEntity gatewayAccountCredentials = aGatewayAccountCredentialsEntity().build();

        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.closed", "lost", true);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
        
        when(stripeGatewayConfig.getRechargeServicesForLivePaymentDisputesFromDate()).thenReturn(Instant.ofEpochSecond(1259539200));
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        when(gatewayAccountService.getGatewayAccount(gatewayAccountId)).thenReturn(Optional.of(gatewayAccount));
        when(gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccount)).thenReturn(Optional.of(gatewayAccountCredentials));
        
        stripeWebhookTaskHandler.process(stripeNotification);
        ArgumentCaptor<DisputeLost> argumentCaptor = ArgumentCaptor.forClass(DisputeLost.class);
        
        verify(stripePaymentProvider).transferDisputeAmount(
                argThat(disputeData -> disputeData.getId().equals("du_1111111111")),
                argThat(c -> c.getExternalId().equals(transaction.getTransactionId())), 
                argThat(g -> g.getId().equals(gatewayAccountId)), 
                argThat(gac -> gac.getExternalId().equals(gatewayAccountCredentials.getExternalId())),
                longThat(transferAmount -> transferAmount.equals(8000L)));
        verify(eventService).emitEvent(argumentCaptor.capture());

        DisputeLost disputeLost = argumentCaptor.getValue();
        assertThat(disputeLost.getEventType(), is("DISPUTE_LOST"));
        assertThat(disputeLost.getResourceType(), is(ResourceType.DISPUTE));
        assertThat(disputeLost.getTimestamp(), is(stripeNotification.getCreated().toInstant()));

        DisputeLostEventDetails eventDetails = (DisputeLostEventDetails) disputeLost.getEventDetails();
        assertThat(eventDetails.getGatewayAccountId(), is("1000"));
        assertThat(eventDetails.getFee(), is(stripeDisputeData.getBalanceTransactionList().get(0).getFee()));
        assertThat(eventDetails.getAmount(), is(stripeDisputeData.getAmount()));
        assertThat(eventDetails.getNetAmount(), is(stripeDisputeData.getBalanceTransactionList().get(0).getNetAmount()));

        verify(mockLogAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Event sent to payment event queue: " + disputeLost.getResourceExternalId();

        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));
    }

    @Test
    void shouldMakeTransferInStripeAndEmitEventForLostDispute_forMultipleBalanceTransactions() throws Exception {
        LedgerTransaction transaction = buildTransaction(true);
        Charge charge = Charge.from(transaction);
        GatewayAccountEntity gatewayAccount = aGatewayAccountEntity().withId(gatewayAccountId).build();
        GatewayAccountCredentialsEntity gatewayAccountCredentials = aGatewayAccountCredentialsEntity().build();

        StripeNotification stripeNotification = getDisputeNotificationWithMultipleBalanceTransactions("charge.dispute.closed", "lost", true);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);

        when(stripeGatewayConfig.getRechargeServicesForLivePaymentDisputesFromDate()).thenReturn(Instant.ofEpochSecond(1259539200));
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        when(gatewayAccountService.getGatewayAccount(gatewayAccountId)).thenReturn(Optional.of(gatewayAccount));
        when(gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccount)).thenReturn(Optional.of(gatewayAccountCredentials));

        stripeWebhookTaskHandler.process(stripeNotification);
        ArgumentCaptor<DisputeLost> argumentCaptor = ArgumentCaptor.forClass(DisputeLost.class);

        verify(stripePaymentProvider).transferDisputeAmount(
                argThat(disputeData -> disputeData.getId().equals("du_1111111111")),
                argThat(c -> c.getExternalId().equals(transaction.getTransactionId())),
                argThat(g -> g.getId().equals(gatewayAccountId)),
                argThat(gac -> gac.getExternalId().equals(gatewayAccountCredentials.getExternalId())),
                longThat(transferAmount -> transferAmount.equals(7950L)));
        verify(eventService).emitEvent(argumentCaptor.capture());

        DisputeLost disputeLost = argumentCaptor.getValue();
        assertThat(disputeLost.getEventType(), is("DISPUTE_LOST"));
        assertThat(disputeLost.getResourceType(), is(ResourceType.DISPUTE));
        assertThat(disputeLost.getTimestamp(), is(stripeNotification.getCreated().toInstant()));

        DisputeLostEventDetails eventDetails = (DisputeLostEventDetails) disputeLost.getEventDetails();
        assertThat(eventDetails.getGatewayAccountId(), is("1000"));
        assertThat(eventDetails.getFee(), is(1501L));
        assertThat(eventDetails.getAmount(), is(stripeDisputeData.getAmount()));
        assertThat(eventDetails.getNetAmount(), is(-7950L));

        verify(mockLogAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Event sent to payment event queue: " + disputeLost.getResourceExternalId();

        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));
    }

    @Test
    void shouldMakeTransferInStripeAndEmitEventForLostDispute_whenDisputeCreatedAfterRechargeEnabledDate_forTestPayment() throws Exception {
        LedgerTransaction transaction = buildTransaction(false);
        Charge charge = Charge.from(transaction);
        GatewayAccountEntity gatewayAccount = aGatewayAccountEntity().withId(gatewayAccountId).build();
        GatewayAccountCredentialsEntity gatewayAccountCredentials = aGatewayAccountCredentialsEntity().build();

        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.closed", "lost", false);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);

        when(stripeGatewayConfig.getRechargeServicesForTestPaymentDisputesFromDate()).thenReturn(Instant.ofEpochSecond(1259539200));
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        when(gatewayAccountService.getGatewayAccount(gatewayAccountId)).thenReturn(Optional.of(gatewayAccount));
        when(gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccount)).thenReturn(Optional.of(gatewayAccountCredentials));

        stripeWebhookTaskHandler.process(stripeNotification);
        ArgumentCaptor<DisputeLost> argumentCaptor = ArgumentCaptor.forClass(DisputeLost.class);

        verify(stripePaymentProvider).transferDisputeAmount(
                argThat(disputeData -> disputeData.getId().equals("du_1111111111")),
                argThat(c -> c.getExternalId().equals(transaction.getTransactionId())),
                argThat(g -> g.getId().equals(gatewayAccountId)),
                argThat(gac -> gac.getExternalId().equals(gatewayAccountCredentials.getExternalId())),
                longThat(transferAmount -> transferAmount.equals(8000L)));
        verify(eventService).emitEvent(argumentCaptor.capture());

        DisputeLost disputeLost = argumentCaptor.getValue();
        assertThat(disputeLost.getEventType(), is("DISPUTE_LOST"));
        assertThat(disputeLost.getResourceType(), is(ResourceType.DISPUTE));
        assertThat(disputeLost.getTimestamp(), is(stripeNotification.getCreated().plusSeconds(1).toInstant()));

        DisputeLostEventDetails eventDetails = (DisputeLostEventDetails) disputeLost.getEventDetails();
        assertThat(eventDetails.getGatewayAccountId(), is("1000"));
        assertThat(eventDetails.getFee(), is(stripeDisputeData.getBalanceTransactionList().get(0).getFee()));
        assertThat(eventDetails.getAmount(), is(stripeDisputeData.getAmount()));
        assertThat(eventDetails.getNetAmount(), is(stripeDisputeData.getBalanceTransactionList().get(0).getNetAmount()));

        verify(mockLogAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Event sent to payment event queue: " + disputeLost.getResourceExternalId();

        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));
    }

    @Test
    void shouldNotMakeTransferForLostDispute_whenDisputeCreatedBeforeRechargeEnabledDate_forLivePayment() throws Exception {
        LedgerTransaction transaction = buildTransaction(true);

        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.closed", "lost", true);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);

        when(stripeGatewayConfig.getRechargeServicesForLivePaymentDisputesFromDate()).thenReturn(Instant.ofEpochSecond(1642579172));
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));

        stripeWebhookTaskHandler.process(stripeNotification);
        ArgumentCaptor<DisputeLost> argumentCaptor = ArgumentCaptor.forClass(DisputeLost.class);

        verify(stripePaymentProvider, never()).transferDisputeAmount(any() ,any(), any(), any(), anyLong());
        verify(eventService).emitEvent(argumentCaptor.capture());

        DisputeLost disputeLost = argumentCaptor.getValue();
        assertThat(disputeLost.getEventType(), is("DISPUTE_LOST"));
        assertThat(disputeLost.getResourceType(), is(ResourceType.DISPUTE));
        assertThat(disputeLost.getTimestamp(), is(stripeNotification.getCreated().toInstant()));

        DisputeLostEventDetails eventDetails = (DisputeLostEventDetails) disputeLost.getEventDetails();
        assertThat(eventDetails.getGatewayAccountId(), is("1000"));
        assertThat(eventDetails.getAmount(), is(stripeDisputeData.getAmount()));
        assertThat(eventDetails.getFee(), is(nullValue()));
        assertThat(eventDetails.getNetAmount(), is(nullValue()));

        verify(mockLogAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = format("Skipping recharging for dispute du_1111111111 for payment %s as it was created before the date we started recharging from", transaction.getTransactionId());
        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));
    }

    @Test
    void shouldNotMakeTransferForLostDispute_whenDisputeCreatedBeforeRechargeEnabledDate_forTestPayment() throws Exception {
        LedgerTransaction transaction = buildTransaction(false);

        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.closed", "lost", true);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);

        when(stripeGatewayConfig.getRechargeServicesForTestPaymentDisputesFromDate()).thenReturn(Instant.ofEpochSecond(1642579172));
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));

        stripeWebhookTaskHandler.process(stripeNotification);
        ArgumentCaptor<DisputeLost> argumentCaptor = ArgumentCaptor.forClass(DisputeLost.class);

        verify(stripePaymentProvider, never()).transferDisputeAmount(any() ,any(), any(), any(), anyLong());
        verify(eventService).emitEvent(argumentCaptor.capture());

        DisputeLost disputeLost = argumentCaptor.getValue();
        assertThat(disputeLost.getEventType(), is("DISPUTE_LOST"));
        assertThat(disputeLost.getResourceType(), is(ResourceType.DISPUTE));
        assertThat(disputeLost.getTimestamp(), is(stripeNotification.getCreated().toInstant()));

        DisputeLostEventDetails eventDetails = (DisputeLostEventDetails) disputeLost.getEventDetails();
        assertThat(eventDetails.getGatewayAccountId(), is("1000"));
        assertThat(eventDetails.getAmount(), is(stripeDisputeData.getAmount()));
        assertThat(eventDetails.getFee(), is(nullValue()));
        assertThat(eventDetails.getNetAmount(), is(nullValue()));

        verify(mockLogAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = format("Skipping recharging for dispute du_1111111111 for payment %s as it was created before the date we started recharging from", transaction.getTransactionId());
        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));
    }

    @Test
    void shouldThrowExceptionWhenGatewayAccountNotFoundForLostDispute() throws Exception {
        LedgerTransaction transaction = buildTransaction(true);

        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.closed", "lost", true);
        
        when(stripeGatewayConfig.getRechargeServicesForLivePaymentDisputesFromDate()).thenReturn(Instant.ofEpochSecond(1259539200));
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        when(gatewayAccountService.getGatewayAccount(gatewayAccountId)).thenReturn(Optional.empty());

        assertThrows(GatewayAccountNotFoundException.class, () -> stripeWebhookTaskHandler.process(stripeNotification));
    }

    @Test
    void shouldThrowExceptionWhenGatewayAccountCredentialsNotFoundForLostDispute() throws Exception {
        LedgerTransaction transaction = buildTransaction(true);
        Charge charge = Charge.from(transaction);
        GatewayAccountEntity gatewayAccount = aGatewayAccountEntity().withId(gatewayAccountId).build();

        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.closed", "lost", true);
        
        when(stripeGatewayConfig.getRechargeServicesForLivePaymentDisputesFromDate()).thenReturn(Instant.ofEpochSecond(1259539200));
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        when(gatewayAccountService.getGatewayAccount(gatewayAccountId)).thenReturn(Optional.of(gatewayAccount));
        when(gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccount)).thenReturn(Optional.empty());

        assertThrows(GatewayAccountCredentialsNotFoundException.class, () -> stripeWebhookTaskHandler.process(stripeNotification));
    }

    @Test
    void shouldNotEmitEventsAndShouldLogWhenDisputeClosedWithWarning() throws Exception {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.closed", "warning_closed", true);

        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        stripeWebhookTaskHandler.process(stripeNotification);

        verify(mockLogAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Skipping dispute notification: [status: warning_closed, type: charge.dispute.closed, payment_intent: pi_1111111111, reason: general]";

        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));

        verifyNoInteractions(eventService);
    }

    @Test
    void shouldReadPayloadProperlyWhenDisputeUpdated() throws Exception {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.updated", "under_review", true);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        stripeWebhookTaskHandler.process(stripeNotification);
        ArgumentCaptor<DisputeEvidenceSubmitted> argumentCaptor = ArgumentCaptor.forClass(DisputeEvidenceSubmitted.class);

        verify(eventService).emitEvent(argumentCaptor.capture());

        DisputeEvidenceSubmitted disputeEvidenceSubmitted = argumentCaptor.getValue();
        assertThat(disputeEvidenceSubmitted.getEventType(), is("DISPUTE_EVIDENCE_SUBMITTED"));
        assertThat(disputeEvidenceSubmitted.getResourceType(), is(ResourceType.DISPUTE));
        assertThat(disputeEvidenceSubmitted.getTimestamp(), is(stripeNotification.getCreated().toInstant()));

        DisputeEvidenceSubmittedEventDetails eventDetails = (DisputeEvidenceSubmittedEventDetails) disputeEvidenceSubmitted.getEventDetails();
        assertThat(eventDetails.getGatewayAccountId(), is("1000"));

        verify(mockLogAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Event sent to payment event queue: " + disputeEvidenceSubmitted.getResourceExternalId();

        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));
    }

    @Test
    void shouldNotEmitEventsAndShouldLogWhenDisputeUnderReviewHasWarning() throws Exception {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.updated", "warning_under_review", true);
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        stripeWebhookTaskHandler.process(stripeNotification);

        verify(mockLogAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Skipping dispute notification: [status: warning_under_review, type: charge.dispute.updated, payment_intent: pi_1111111111, reason: general]";

        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));

        verifyNoInteractions(eventService);
    }

    @Test
    void shouldThrowExceptionWhenNotDispute() throws JsonProcessingException {
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.solved", "under_review", true);
        var thrown = assertThrows(RuntimeException.class, () -> stripeWebhookTaskHandler.process(stripeNotification));
        assertThat(thrown.getMessage(), is("Unknown webhook task: charge.dispute.solved"));
    }

    @Test
    void shouldThrowExceptionWhenDisputeClosedAndUnknownStatus() throws JsonProcessingException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.closed", "charge_refunded", true);
        var thrown = assertThrows(RuntimeException.class, () -> stripeWebhookTaskHandler.process(stripeNotification));
        assertThat(thrown.getMessage(), is("Unknown stripe dispute status: [status: charge_refunded, payment_intent: pi_1111111111]"));
    }

    @Test
    void shouldLogWhenDisputeUpdatedAndUnknownStatus() throws Exception {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.updated", "needs_response", true);

        stripeWebhookTaskHandler.process(stripeNotification);
        verify(mockLogAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Skipping dispute updated notification: [status: needs_response, payment_intent: pi_1111111111]";

        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));
    }

    @Test
    void shouldThrowExceptionWhenNoLedgerTransactionFound() throws JsonProcessingException {
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.empty());
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.created", "needs_response", true);
        var thrown = assertThrows(RuntimeException.class, () -> stripeWebhookTaskHandler.process(stripeNotification));
        assertThat(thrown.getMessage(), is("LedgerTransaction with gateway transaction id [pi_1111111111] not found"));
    }

    @Test
    void shouldTriggerSubmitEvidence_whenDisputeCreated_andCardNumbersMatch() throws JsonProcessingException, GatewayException {
        String disputeId = "du_1111111111";
        String evidenceText = "losing_evidence";
        String transactionId = "external-id";
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .withCardDetails(new CardDetails("John Doe", null, null,
                        "0259", "400000", null, null))
                .isLive(false)
                .build();
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.created", "needs_response", false);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        when(stripePaymentProvider.submitTestDisputeEvidence(disputeId, evidenceText, transactionId)).thenReturn(stripeDisputeData);
        stripeWebhookTaskHandler.process(stripeNotification);

        String resourceExternalId = RandomIdGenerator.idFromExternalId(stripeDisputeData.getId());
        var disputeCreated = DisputeCreated.from(resourceExternalId, stripeDisputeData, transaction,
                stripeDisputeData.getDisputeCreated().toInstant());
        var paymentDisputed = PaymentDisputed.from(transaction, stripeDisputeData.getDisputeCreated().toInstant());
        var refundAvailabilityUpdated = RefundAvailabilityUpdated.from(
                transaction, ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE, Instant.parse(fixedClockDateTime));

        verify(eventService).emitEvent(disputeCreated);
        verify(eventService).emitEvent(paymentDisputed);
        verify(eventService).emitEvent(refundAvailabilityUpdated);

        verify(mockLogAppender, times(4)).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String eventExpectedLogMessage = "Event sent to payment event queue: " + disputeCreated.getResourceExternalId();
        String submitEvidenceExpectedLogMessage = "Updated dispute [du_1111111111] with evidence [losing_evidence] for transaction [external-id]";

        assertThat(logStatement.get(0).getFormattedMessage(), is(eventExpectedLogMessage));
        assertThat(logStatement.get(3).getFormattedMessage(), is(submitEvidenceExpectedLogMessage));
    }

    @Test
    void shouldThrowExceptionIfNoCardDetailsPresent_whenHandlingSubmitTestEvidence() throws JsonProcessingException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .withCardDetails(null)
                .isLive(false)
                .build();
        StripeNotification stripeNotification = getDisputeNotification("charge.dispute.created", "needs_response", false);
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));

        var thrown = assertThrows(RuntimeException.class, () -> stripeWebhookTaskHandler.process(stripeNotification));
        assertThat(thrown.getMessage(), is("Card details are not yet available on ledger transaction to submit test evidence"));
    }

    private LedgerTransaction buildTransaction(boolean live) {
        return aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(gatewayAccountId)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(live)
                .withNetAmount(-8000L)
                .withAmount(6500L)
                .withFee(1500L)
                .build();
    }

    private StripeNotification getDisputeNotification(String webhookType, String status, boolean liveStripeAccount) throws JsonProcessingException {
        return getDisputeNotification(webhookType, status, liveStripeAccount, STRIPE_NOTIFICATION_CHARGE_DISPUTE);
    }

    private StripeNotification getDisputeNotificationWithMultipleBalanceTransactions(String webhookType, String status, boolean liveStripeAccount) throws JsonProcessingException {
        return getDisputeNotification(webhookType, status, liveStripeAccount, STRIPE_NOTIFICATION_CHARGE_DISPUTE_LOST_WITH_MULTIPLE_BALANCE_TRANSACTIONS);
    }

    private StripeNotification getDisputeNotification(String webhookType, String status, boolean liveStripeAccount, String template) throws JsonProcessingException {
        String payload = TestTemplateResourceLoader.load(template)
                .replace(PLACEHOLDER_TYPE, webhookType)
                .replace(PLACEHOLDER_STATUS, status);
        if (!liveStripeAccount) {
            payload = payload.replaceAll("\"livemode\": true", "\"livemode\": false");
        }
        return objectMapper.readValue(payload, StripeNotification.class);
    }
}
