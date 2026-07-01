package uk.gov.pay.connector.queue.tasks;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.queue.tasks.handlers.AdyenWebhookTaskHandler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;


@ExtendWith(MockitoExtension.class)
class AdyenWebhookTaskHandlerTest {

    @Mock
    private GatewayAccountService mockGatewayAccountService;

    @Mock
    private ChargeService mockChargeService;

    @Mock
    private ChargeNotificationProcessor mockChargeNotificationProcessor;

    @Mock
    private AdyenNotificationService mockAdyenNotificationService;

    @Mock
    private Charge mockCharge;

    @Mock
    NotificationRequest mockNotificationRequest;

    @Mock
    NotificationRequestItem mockNotificationItem;

    @Mock
    GatewayAccountEntity gatewayAccount;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @InjectMocks
    private AdyenWebhookTaskHandler adyenWebhookTaskHandler;

    private final String gatewayTransactionId = "adyen-payment-id-1";

    private final String payload = "payload";
    private final Date eventDate = Date.from(Instant.parse("2026-05-19T10:15:30Z"));
    private final long gatewayAccountId = 123L;


    @BeforeEach
    void setUp() {
        Logger root = (Logger) LoggerFactory.getLogger(AdyenWebhookTaskHandler.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    void shouldProcessSuccessfulCaptureNotificationForConnectorCharge() {
        when(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload))
                .thenReturn(mockNotificationRequest);
        when(mockAdyenNotificationService.extractNotificationItems(mockNotificationRequest))
                .thenReturn(List.of(mockNotificationItem));
        when(mockNotificationItem.getEventCode()).thenReturn(NotificationRequestItem.EVENT_CODE_CAPTURE);
        when(mockNotificationItem.getOriginalReference()).thenReturn(gatewayTransactionId);
        when(mockNotificationItem.isSuccess()).thenReturn(true);
        when(mockNotificationItem.getEventDate()).thenReturn(eventDate);


        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(ADYEN.getName(),
                gatewayTransactionId)).thenReturn(Optional.of(mockCharge));

        when(mockCharge.isHistoric()).thenReturn(false);

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        verify(mockChargeService).findByProviderAndTransactionIdFromDbOrLedger(ADYEN.getName(),
                gatewayTransactionId);

        verify(mockChargeNotificationProcessor).invoke(gatewayTransactionId, mockCharge,
                CAPTURED, ZonedDateTime.ofInstant(eventDate.toInstant(), ZoneId.of("UTC")));
    }

    @Test
    void shouldProcessCaptureFailedMessageFromTaskQueueForConnectorCharge() {
        when(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload))
                .thenReturn(mockNotificationRequest);
        when(mockAdyenNotificationService.extractNotificationItems(mockNotificationRequest))
                .thenReturn(List.of(mockNotificationItem));
        when(mockNotificationItem.getEventCode()).thenReturn(NotificationRequestItem.EVENT_CODE_CAPTURE);
        when(mockNotificationItem.getOriginalReference()).thenReturn(gatewayTransactionId);
        when(mockNotificationItem.isSuccess()).thenReturn(false);
        when(mockNotificationItem.getEventDate()).thenReturn(eventDate);

        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(ADYEN.getName(),
                gatewayTransactionId)).thenReturn(Optional.of(mockCharge));

        when(mockCharge.isHistoric()).thenReturn(false);

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        verify(mockChargeNotificationProcessor).invoke(gatewayTransactionId, mockCharge,
                CAPTURE_ERROR, ZonedDateTime.ofInstant(eventDate.toInstant(), ZoneId.of("UTC")));

        verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();

        assertThat(loggingEvents.stream().anyMatch(event -> event.getFormattedMessage()
                .equals("Capture failed")), is(true));

    }

    @Test
    void shouldProcessSuccessfulCaptureMessageFromTaskQueueForLedgerCharge() {
        when(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload))
                .thenReturn(mockNotificationRequest);
        when(mockAdyenNotificationService.extractNotificationItems(mockNotificationRequest))
                .thenReturn(List.of(mockNotificationItem));
        when(mockNotificationItem.getEventCode()).thenReturn(NotificationRequestItem.EVENT_CODE_CAPTURE);
        when(mockNotificationItem.getOriginalReference()).thenReturn(gatewayTransactionId);
        when(mockNotificationItem.isSuccess()).thenReturn(true);

        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(ADYEN.getName(),
                gatewayTransactionId)).thenReturn(Optional.of(mockCharge));

        when(mockCharge.getGatewayAccountId()).thenReturn(gatewayAccountId);
        when(mockGatewayAccountService.getGatewayAccount(gatewayAccountId))
                .thenReturn(Optional.of(gatewayAccount));
        when(mockCharge.isHistoric()).thenReturn(true);
        when(mockGatewayAccountService.getGatewayAccount(gatewayAccountId))
                .thenReturn(Optional.of(gatewayAccount));

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        verify(mockChargeNotificationProcessor).processCaptureNotificationForExpungedCharge(
                gatewayAccount, gatewayTransactionId, mockCharge, CAPTURED);
        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    void shouldProcessFailedCaptureMessageFromTaskQueueForLedgerCharge() {
        when(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload))
                .thenReturn(mockNotificationRequest);

        when(mockAdyenNotificationService.extractNotificationItems(mockNotificationRequest))
                .thenReturn(List.of(mockNotificationItem));
        when(mockNotificationItem.getEventCode()).thenReturn(NotificationRequestItem.EVENT_CODE_CAPTURE);
        when(mockNotificationItem.getOriginalReference()).thenReturn(gatewayTransactionId);
        when(mockNotificationItem.isSuccess()).thenReturn(false);

        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(ADYEN.getName(),
                gatewayTransactionId)).thenReturn(Optional.of(mockCharge));

        when(mockCharge.isHistoric()).thenReturn(true);
        when(mockCharge.getGatewayAccountId()).thenReturn(gatewayAccountId);

        when(mockGatewayAccountService.getGatewayAccount(gatewayAccountId))
                .thenReturn(Optional.of(gatewayAccount));

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        verify(mockChargeNotificationProcessor).processCaptureNotificationForExpungedCharge(gatewayAccount,
                gatewayTransactionId,
                mockCharge,
                CAPTURE_ERROR
        );
        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    void shouldLogWarningWhenChargeDoesNotExistInConnectorOrLedger() {
        when(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload))
                .thenReturn(mockNotificationRequest);
        when(mockAdyenNotificationService.extractNotificationItems(mockNotificationRequest))
                .thenReturn(List.of(mockNotificationItem));
        when(mockNotificationItem.getEventCode()).thenReturn(NotificationRequestItem.EVENT_CODE_CAPTURE);
        when(mockNotificationItem.getOriginalReference()).thenReturn(gatewayTransactionId);
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(ADYEN.getName(),
                gatewayTransactionId)).thenReturn(Optional.empty());

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockChargeNotificationProcessor, never()).processCaptureNotificationForExpungedCharge(any(), any(),
                any(), any());
        verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();

        assertThat(loggingEvents.stream().anyMatch(event -> event.getFormattedMessage()
                .equals("Charge not found in Connector or Ledger for Adyen capture webhook")), is(true));
    }
}
