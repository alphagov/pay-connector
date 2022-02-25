package uk.gov.pay.connector.queue.tasks;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import uk.gov.pay.connector.gateway.stripe.response.StripeNotification;
import uk.gov.pay.connector.queue.tasks.handlers.StripeWebhookTaskHandler;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_NOTIFICATION_CHARGE_DISPUTE_CREATED;

@ExtendWith(MockitoExtension.class)
public class StripeWebhookTaskHandlerTest {

    @Mock
    private Appender<ILoggingEvent> mockLogAppender;
    @Mock
    private LedgerService ledgerService;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    private StripeWebhookTaskHandler stripeWebhookTaskHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String payload;

    @BeforeEach
    void setUp() {
        stripeWebhookTaskHandler = new StripeWebhookTaskHandler(ledgerService);
        payload = TestTemplateResourceLoader.load(STRIPE_NOTIFICATION_CHARGE_DISPUTE_CREATED);
        Logger logger = (Logger) LoggerFactory.getLogger(StripeWebhookTaskHandler.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(mockLogAppender);
    }

    @Test
    void shouldReadPayloadProperly() throws JsonProcessingException {
        LedgerTransaction transaction = aValidLedgerTransaction()
                .withExternalId("external-id")
                .withGatewayAccountId(1000L)
                .withGatewayTransactionId("gateway-transaction-id")
                .isLive(true)
                .build();
        StripeNotification stripeNotification = objectMapper.readValue(payload, StripeNotification.class);
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.of(transaction));
        stripeWebhookTaskHandler.process(stripeNotification);
        verify(mockLogAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(logEvents.stream().anyMatch(e -> e.getFormattedMessage().contains("Received event for Stripe Webhook Task: du_1111111111")), is(true));
    }

    @Test
    void shouldThrowExceptionWhenNotDispute() throws JsonProcessingException {
        String finalPayload = payload.replace("charge.dispute.created", "charge.dispute.solved");
        StripeNotification stripeNotification = objectMapper.readValue(finalPayload, StripeNotification.class);
        var thrown = assertThrows(RuntimeException.class, () -> stripeWebhookTaskHandler.process(stripeNotification));
        assertThat(thrown.getMessage(), is("Unknown webhook task: charge.dispute.solved"));
    }

    @Test
    void shouldThrowExceptionWhenMoreThanOneBalanceTransactionPresent() throws JsonProcessingException {
        String finalPayload = payload.replace("],", ",{}],");
        StripeNotification stripeNotification = objectMapper.readValue(finalPayload, StripeNotification.class);
        var thrown = assertThrows(RuntimeException.class, () -> stripeWebhookTaskHandler.process(stripeNotification));
        assertThat(thrown.getMessage(), is("Dispute data has too many balance_transactions"));
    }

    @Test
    void shouldThrowExceptionWhenNoLedgerTransactionFound() throws JsonProcessingException {
        when(ledgerService.getTransactionForProviderAndGatewayTransactionId(any(), any()))
                .thenReturn(Optional.empty());
        StripeNotification stripeNotification = objectMapper.readValue(payload, StripeNotification.class);
        var thrown = assertThrows(RuntimeException.class, () -> stripeWebhookTaskHandler.process(stripeNotification));
        assertThat(thrown.getMessage(), is("LedgerTransaction with gateway transaction id [pi_1111111111] not found"));
    }
}
