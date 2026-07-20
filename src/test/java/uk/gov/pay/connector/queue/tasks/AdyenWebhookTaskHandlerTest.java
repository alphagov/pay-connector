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
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenCancellationNotificationHandler;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenCaptureNotificationHandler;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenRefundNotificationHandler;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenWebhookTaskHandler;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_REFUND_FAILURE_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_REFUND_SUCCESS_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class AdyenWebhookTaskHandlerTest {

    @Mock
    private ChargeService mockChargeService;

    @Mock
    private AdyenCaptureNotificationHandler mockAdyenCaptureNotificationHandler;

    @Mock
    private AdyenRefundNotificationHandler mockAdyenRefundNotificationHandler;

    @Mock
    private AdyenCancellationNotificationHandler mockAdyenCancellationNotificationHandler;

    @Mock
    private AdyenNotificationService mockAdyenNotificationService;

    @Mock
    private Charge mockCharge;

    @Mock
    NotificationRequest mockNotificationRequest;

    @Mock
    NotificationRequestItem mockNotificationItem;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @InjectMocks
    private AdyenWebhookTaskHandler adyenWebhookTaskHandler;

    private final String gatewayTransactionId = "adyen-payment-id-1";

    private final String payload = "payload";


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
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(ADYEN.getName(),
                gatewayTransactionId)).thenReturn(Optional.of(mockCharge));

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        verify(mockChargeService).findByProviderAndTransactionIdFromDbOrLedger(ADYEN.getName(),
                gatewayTransactionId);
        verify(mockAdyenCaptureNotificationHandler).process(mockNotificationItem, mockCharge);
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

        verify(mockAdyenCaptureNotificationHandler, never()).process(any(), any());
        verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());

        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();

        assertThat(loggingEvents.stream().anyMatch(event -> event.getFormattedMessage()
                .equals("Charge not found in Connector or Ledger for Adyen capture webhook")), is(true));
    }

    @Test
    void shouldNotProcessRefundNotificationWhenChargeNotFound() {
        when(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload))
                .thenReturn(mockNotificationRequest);
        when(mockAdyenNotificationService.extractNotificationItems(mockNotificationRequest))
                .thenReturn(List.of(mockNotificationItem));
        when(mockNotificationItem.getEventCode()).thenReturn(NotificationRequestItem.EVENT_CODE_REFUND);
        when(mockNotificationItem.getOriginalReference()).thenReturn(gatewayTransactionId);
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(ADYEN.getName(), gatewayTransactionId))
                .thenReturn(Optional.empty());

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        verify(mockAdyenRefundNotificationHandler, never()).process(any(), any());
    }

    @Test
    void shouldProcessRefundNotificationForConnectorCharge() {
        when(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload))
                .thenReturn(mockNotificationRequest);
        when(mockAdyenNotificationService.extractNotificationItems(mockNotificationRequest))
                .thenReturn(List.of(mockNotificationItem));
        when(mockNotificationItem.getEventCode()).thenReturn(NotificationRequestItem.EVENT_CODE_REFUND);
        when(mockNotificationItem.getOriginalReference()).thenReturn(gatewayTransactionId);
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(ADYEN.getName(), gatewayTransactionId))
                .thenReturn(Optional.of(mockCharge));

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        verify(mockAdyenRefundNotificationHandler).process(mockNotificationItem, mockCharge);
    }

    @Test
    void shouldProcessCancellationNotificationForConnectorCharge() {
        when(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload))
                .thenReturn(mockNotificationRequest);
        when(mockAdyenNotificationService.extractNotificationItems(mockNotificationRequest))
                .thenReturn(List.of(mockNotificationItem));
        when(mockNotificationItem.getEventCode()).thenReturn(NotificationRequestItem.EVENT_CODE_CANCELLATION);
        when(mockNotificationItem.getOriginalReference()).thenReturn(gatewayTransactionId);
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(ADYEN.getName(), gatewayTransactionId))
                .thenReturn(Optional.of(mockCharge));

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        verify(mockAdyenCancellationNotificationHandler).process(mockNotificationItem, mockCharge);
        verify(mockAdyenRefundNotificationHandler, never()).process(any(), any());
        verify(mockAdyenCaptureNotificationHandler, never()).process(any(), any());
    }

    @Test
    void shouldIgnoreUnsupportedNotificationItem() {
        when(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload))
                .thenReturn(mockNotificationRequest);
        when(mockAdyenNotificationService.extractNotificationItems(mockNotificationRequest))
                .thenReturn(List.of(mockNotificationItem));
        when(mockNotificationItem.getEventCode()).thenReturn("UNSUPPORTED_EVENT");
        when(mockNotificationItem.getOriginalReference()).thenReturn(gatewayTransactionId);
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(ADYEN.getName(), gatewayTransactionId))
                .thenReturn(Optional.of(mockCharge));

        adyenWebhookTaskHandler.processAdyenWebhookNotification(payload);

        verify(mockAdyenRefundNotificationHandler, never()).process(any(), any());
        verify(mockAdyenCancellationNotificationHandler, never()).process(any(), any());
        verify(mockAdyenCaptureNotificationHandler, never()).process(any(), any());
        verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
        assertThat(loggingEventArgumentCaptor.getAllValues().stream()
                .anyMatch(event -> event.getFormattedMessage().equals("Ignoring unsupported Adyen webhook item")), is(true));
    }

    @Test
    void should_transition_refund_in_REFUND_SUBMITTED_state_to_refund_handler_for_successful_REFUND_event() throws IOException {
        var notification = NotificationRequest.fromJson(load(ADYEN_REFUND_SUCCESS_NOTIFICATION));
        var charge = Charge.from(ChargeEntityFixture.aValidChargeEntity().build());

        given(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(any()))
                .willReturn(notification);
        given(mockAdyenNotificationService.extractNotificationItems(notification))
                .willReturn(notification.getNotificationItems());
        given(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(any(), any()))
                .willReturn(Optional.of(charge));

        adyenWebhookTaskHandler.processAdyenWebhookNotification("refund-successful-notification");

        then(mockAdyenRefundNotificationHandler)
                .should()
                .process(any(), org.mockito.ArgumentMatchers.eq(charge));
    }

    @Test
    void should_transition_refund_in_REFUND_SUBMITTED_state_to_refund_handler_for_failed_REFUND_event() throws IOException {
        var notification = NotificationRequest.fromJson(load(ADYEN_REFUND_FAILURE_NOTIFICATION));
        var charge = Charge.from(ChargeEntityFixture.aValidChargeEntity().build());

        given(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(any()))
                .willReturn(notification);
        given(mockAdyenNotificationService.extractNotificationItems(notification))
                .willReturn(notification.getNotificationItems());
        given(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(any(), any()))
                .willReturn(Optional.of(charge));

        adyenWebhookTaskHandler.processAdyenWebhookNotification("refund-failed-notification");

        then(mockAdyenRefundNotificationHandler)
                .should()
                .process(any(), org.mockito.ArgumentMatchers.eq(charge));
    }
}
