package uk.gov.pay.connector.queue.tasks.handlers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.event.KeyValuePair;
import org.slf4j.event.Level;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenCancellationNotificationHandler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

@ExtendWith(MockitoExtension.class)
class AdyenCancellationNotificationHandlerTest {

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

    @InjectMocks
    private AdyenCancellationNotificationHandler adyenCancellationNotificationHandler;

    private final String gatewayTransactionId = "adyen-payment-id-1";

    private final String payload = "payload";
    private final Date eventDate = Date.from(Instant.parse("2026-05-19T10:15:30Z"));

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenCancellationNotificationHandler.class);

    @ParameterizedTest
    @CsvSource({"true,USER CANCEL SUBMITTED,USER CANCELLED",
            "true,SYSTEM CANCEL SUBMITTED,SYSTEM CANCELLED",
            "true,USER CANCEL ERROR,USER CANCELLED",
            "false,USER CANCEL SUBMITTED,USER CANCEL ERROR",
            "false,SYSTEM CANCEL SUBMITTED,SYSTEM CANCEL ERROR",
            "false,SYSTEM CANCEL SUBMITTED,SYSTEM CANCEL ERROR",
    })
    void shouldProcessCancelNotificationForConnectorCharge(Boolean success, String currentStatus, String expectedStatus) {
        when(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload))
                .thenReturn(mockNotificationRequest);
        when(mockAdyenNotificationService.extractNotificationItems(mockNotificationRequest))
                .thenReturn(List.of(mockNotificationItem));
        when(mockNotificationItem.getOriginalReference()).thenReturn(gatewayTransactionId);
        when(mockNotificationItem.isSuccess()).thenReturn(success);
        when(mockNotificationItem.getEventDate()).thenReturn(eventDate);
        when(mockCharge.getStatus()).thenReturn(currentStatus);

        when(mockCharge.isHistoric()).thenReturn(false);

        NotificationRequest notificationRequest =
                mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload);

        NotificationRequestItem item = mockAdyenNotificationService.extractNotificationItems(notificationRequest).getFirst();

        adyenCancellationNotificationHandler.process(item, mockCharge);

        verify(mockChargeNotificationProcessor).invoke(gatewayTransactionId, mockCharge,
                ChargeStatus.fromString(expectedStatus), ZonedDateTime.ofInstant(eventDate.toInstant(), ZoneId.of("UTC")));
    }

    @ParameterizedTest
    @CsvSource({"false,EXPIRED",
            "false,AUTHORISATION ERROR CANCELLED",
            "true,CAPTURE QUEUED"
    })
    void shouldIgnoreCancelNotificationAndLogWarningWhenInUnexpectedState(Boolean success, String currentStatus) {
        when(mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload))
                .thenReturn(mockNotificationRequest);
        when(mockAdyenNotificationService.extractNotificationItems(mockNotificationRequest))
                .thenReturn(List.of(mockNotificationItem));
        when(mockNotificationItem.getOriginalReference()).thenReturn(gatewayTransactionId);
        when(mockNotificationItem.isSuccess()).thenReturn(success);
        when(mockCharge.getStatus()).thenReturn(currentStatus);
        when(mockCharge.isHistoric()).thenReturn(false);
        when(mockCharge.getExternalId()).thenReturn("someId");

        NotificationRequest notificationRequest =
                mockAdyenNotificationService.deserialisePayloadToNotificationRequest(payload);
        NotificationRequestItem item = mockAdyenNotificationService.extractNotificationItems(notificationRequest).getFirst();

        adyenCancellationNotificationHandler.process(item, mockCharge);

        verifyNoInteractions(mockChargeNotificationProcessor);
        
        var loggingMessage = String.format("Charge is not in expected state for cancellation: %s", currentStatus);
        var loggingEvents = logs.getEvents();
        assertThat(loggingEvents, everyItem(hasProperty("level", is(Level.WARN))));
        assertThat(loggingEvents, everyItem(hasProperty("message", is(loggingMessage))));

        var keyValuePairs = loggingEvents.stream()
                .flatMap(event -> event.getKeyValuePairs().stream())
                .toList();

        assertThat(keyValuePairs, hasItems(
                new KeyValuePair(PAYMENT_EXTERNAL_ID, mockCharge.getExternalId()),
                new KeyValuePair("gateway_transaction_id", gatewayTransactionId),
                new KeyValuePair("status", currentStatus),
                new KeyValuePair("success", item.isSuccess())));
    }

    @Test
    void shouldIgnoreHistoricChargeAndLogInfo() {
        when(mockNotificationItem.getOriginalReference()).thenReturn(gatewayTransactionId);
        when(mockCharge.isHistoric()).thenReturn(true);
        when(mockCharge.getExternalId()).thenReturn("someId");

        adyenCancellationNotificationHandler.process(mockNotificationItem, mockCharge);

        verifyNoInteractions(mockChargeNotificationProcessor);
        var loggingEvents = logs.getEvents();
        assertThat(loggingEvents, everyItem(hasProperty("level", is(Level.INFO))));
        assertThat(loggingEvents, everyItem(hasProperty("message", is("Ignored Adyen cancellation webhook for historic charge"))));

        var keyValuePairs = loggingEvents.stream()
                .flatMap(event -> event.getKeyValuePairs().stream())
                .toList();
        assertThat(keyValuePairs, hasItems(
                new KeyValuePair(PAYMENT_EXTERNAL_ID, mockCharge.getExternalId()),
                new KeyValuePair("gateway_transaction_id", gatewayTransactionId)));
    }
}
