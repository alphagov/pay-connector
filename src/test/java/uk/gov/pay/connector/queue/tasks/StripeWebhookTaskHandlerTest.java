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
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeCreatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeEvidenceSubmittedEventDetails;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeLostEventDetails;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeWonEventDetails;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.events.model.dispute.DisputeCreated;
import uk.gov.pay.connector.events.model.dispute.DisputeEvidenceSubmitted;
import uk.gov.pay.connector.events.model.dispute.DisputeLost;
import uk.gov.pay.connector.events.model.dispute.DisputeWon;
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.queue.tasks.dispute.StripeDisputeData;
import uk.gov.pay.connector.queue.tasks.handlers.StripeWebhookTaskHandler;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_CHARGE_DISPUTE;

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

    private StripeWebhookTaskHandler stripeWebhookTaskHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String payload;
    private final String PLACEHOLDER_TYPE = "{{type}}";
    private final String PLACEHOLDER_STATUS = "{{status}}";

    @BeforeEach
    void setUp() {
        stripeWebhookTaskHandler = new StripeWebhookTaskHandler(ledgerService, eventService);
        Logger logger = (Logger) LoggerFactory.getLogger(StripeWebhookTaskHandler.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(mockLogAppender);
        payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_CHARGE_DISPUTE);
    }

    @Test
    void shouldReadPayloadProperlyWhenDisputeCreated() throws JsonProcessingException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        String finalPayload = payload
                .replace(PLACEHOLDER_TYPE, "charge.dispute.created")
                .replace(PLACEHOLDER_STATUS, "needs_response");
        StripeNotification stripeNotification = objectMapper.readValue(finalPayload, StripeNotification.class);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        stripeWebhookTaskHandler.process(stripeNotification);
        ArgumentCaptor<DisputeCreated> disputeCreatedArgumentCaptor = ArgumentCaptor.forClass(DisputeCreated.class);
        verify(eventService).emitEvent(disputeCreatedArgumentCaptor.capture());

        DisputeCreated disputeCreated = disputeCreatedArgumentCaptor.getValue();
        assertThat(disputeCreated.getEventType(), is("DISPUTE_CREATED"));
        assertThat(disputeCreated.getResourceType(), is(ResourceType.DISPUTE));
        assertThat(disputeCreated.getTimestamp(), is(stripeDisputeData.getDisputeCreated()));

        DisputeCreatedEventDetails eventDetails = (DisputeCreatedEventDetails) disputeCreated.getEventDetails();
        assertThat(eventDetails.getReason(), is("general"));
        assertThat(eventDetails.getFee(), is(1500L));
        assertThat(eventDetails.getAmount(), is(6500L));
        assertThat(eventDetails.getNetAmount(), is(-8000L));

        verify(mockLogAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Event sent to payment event queue: " + disputeCreated.getResourceExternalId();

        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));
    }

    @Test
    void shouldReadPayloadProperlyWhenDisputeClosedAndStatusWon() throws JsonProcessingException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        String finalPayload = payload
                .replace(PLACEHOLDER_TYPE, "charge.dispute.closed")
                .replace(PLACEHOLDER_STATUS, "won");
        StripeNotification stripeNotification = objectMapper.readValue(finalPayload, StripeNotification.class);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        stripeWebhookTaskHandler.process(stripeNotification);
        ArgumentCaptor<DisputeWon> argumentCaptor = ArgumentCaptor.forClass(DisputeWon.class);

        verify(eventService).emitEvent(argumentCaptor.capture());

        DisputeWon disputeWon = argumentCaptor.getValue();
        assertThat(disputeWon.getEventType(), is("DISPUTE_WON"));
        assertThat(disputeWon.getResourceType(), is(ResourceType.DISPUTE));
        assertThat(disputeWon.getTimestamp(), is(stripeNotification.getCreated()));

        DisputeWonEventDetails eventDetails = (DisputeWonEventDetails) disputeWon.getEventDetails();
        assertThat(eventDetails.getGatewayAccountId(), is("1000"));

        verify(mockLogAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Event sent to payment event queue: " + disputeWon.getResourceExternalId();

        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));
    }

    @Test
    void shouldReadPayloadProperlyWhenDisputeClosedAndStatusLost() throws JsonProcessingException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .withNetAmount(-8000L)
                .withAmount(6500L)
                .withFee(1500L)
                .build();
        String finalPayload = payload
                .replace(PLACEHOLDER_TYPE, "charge.dispute.closed")
                .replace(PLACEHOLDER_STATUS, "lost");
        StripeNotification stripeNotification = objectMapper.readValue(finalPayload, StripeNotification.class);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        stripeWebhookTaskHandler.process(stripeNotification);
        ArgumentCaptor<DisputeLost> argumentCaptor = ArgumentCaptor.forClass(DisputeLost.class);

        verify(eventService).emitEvent(argumentCaptor.capture());

        DisputeLost disputeLost = argumentCaptor.getValue();
        assertThat(disputeLost.getEventType(), is("DISPUTE_LOST"));
        assertThat(disputeLost.getResourceType(), is(ResourceType.DISPUTE));
        assertThat(disputeLost.getTimestamp(), is(stripeNotification.getCreated()));

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
    void shouldReadPayloadProperlyWhenDisputeUpdated() throws JsonProcessingException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        String finalPayload = payload
                .replace(PLACEHOLDER_TYPE, "charge.dispute.updated")
                .replace(PLACEHOLDER_STATUS, "under_review");
        StripeNotification stripeNotification = objectMapper.readValue(finalPayload, StripeNotification.class);
        StripeDisputeData stripeDisputeData = objectMapper.readValue(stripeNotification.getObject(), StripeDisputeData.class);
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        stripeWebhookTaskHandler.process(stripeNotification);
        ArgumentCaptor<DisputeEvidenceSubmitted> argumentCaptor = ArgumentCaptor.forClass(DisputeEvidenceSubmitted.class);

        verify(eventService).emitEvent(argumentCaptor.capture());

        DisputeEvidenceSubmitted disputeEvidenceSubmitted = argumentCaptor.getValue();
        assertThat(disputeEvidenceSubmitted.getEventType(), is("DISPUTE_EVIDENCE_SUBMITTED"));
        assertThat(disputeEvidenceSubmitted.getResourceType(), is(ResourceType.DISPUTE));
        assertThat(disputeEvidenceSubmitted.getTimestamp(), is(stripeNotification.getCreated()));

        DisputeEvidenceSubmittedEventDetails eventDetails = (DisputeEvidenceSubmittedEventDetails) disputeEvidenceSubmitted.getEventDetails();
        assertThat(eventDetails.getGatewayAccountId(), is("1000"));

        verify(mockLogAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Event sent to payment event queue: " + disputeEvidenceSubmitted.getResourceExternalId();

        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));
    }

    @Test
    void shouldThrowExceptionWhenNotDispute() throws JsonProcessingException {
        String finalPayload = payload
                .replace(PLACEHOLDER_TYPE, "charge.dispute.solved")
                .replace(PLACEHOLDER_STATUS, "under_review");
        StripeNotification stripeNotification = objectMapper.readValue(finalPayload, StripeNotification.class);
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
        String finalPayload = payload
                .replace(PLACEHOLDER_TYPE, "charge.dispute.closed")
                .replace(PLACEHOLDER_STATUS, "charge_refunded");
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        StripeNotification stripeNotification = objectMapper.readValue(finalPayload, StripeNotification.class);
        var thrown = assertThrows(RuntimeException.class, () -> stripeWebhookTaskHandler.process(stripeNotification));
        assertThat(thrown.getMessage(), is("Unknown stripe dispute closed status: [status: charge_refunded, payment_intent: pi_1111111111]"));
    }

    @Test
    void shouldLogWhenDisputeUpdatedAndUnknownStatus() throws JsonProcessingException {
        String finalPayload = payload
                .replace(PLACEHOLDER_TYPE, "charge.dispute.updated")
                .replace(PLACEHOLDER_STATUS, "needs_response");
        StripeNotification stripeNotification = objectMapper.readValue(finalPayload, StripeNotification.class);

        stripeWebhookTaskHandler.process(stripeNotification);
        verify(mockLogAppender).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> logStatement = loggingEventArgumentCaptor.getAllValues();
        String expectedLogMessage = "Skipping dispute updated notification: [status: needs_response, payment_intent: pi_1111111111]";

        assertThat(logStatement.get(0).getFormattedMessage(), Is.is(expectedLogMessage));
    }

    @Test
    void shouldThrowExceptionWhenMoreThanOneBalanceTransactionPresent() throws JsonProcessingException {
        String finalPayload = payload
                .replace(PLACEHOLDER_TYPE, "charge.dispute.created")
                .replace(PLACEHOLDER_STATUS, "needs_response")
                .replace("],", ",{}],");
        StripeNotification stripeNotification = objectMapper.readValue(finalPayload, StripeNotification.class);
        var thrown = assertThrows(RuntimeException.class, () -> stripeWebhookTaskHandler.process(stripeNotification));
        assertThat(thrown.getMessage(), is("Dispute data has too many balance_transactions"));
    }

    @Test
    void shouldThrowExceptionWhenNoLedgerTransactionFound() throws JsonProcessingException {
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.empty());
        String finalPayload = payload
                .replace(PLACEHOLDER_TYPE, "charge.dispute.created")
                .replace(PLACEHOLDER_STATUS, "needs_response");
        StripeNotification stripeNotification = objectMapper.readValue(finalPayload, StripeNotification.class);
        var thrown = assertThrows(RuntimeException.class, () -> stripeWebhookTaskHandler.process(stripeNotification));
        assertThat(thrown.getMessage(), is("LedgerTransaction with gateway transaction id [pi_1111111111] not found"));
    }
}
