package uk.gov.pay.connector.queue.tasks.handlers;

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
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenCaptureNotificationHandler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdyenCaptureNotificationHandlerTest {

    @Mock
    private ChargeNotificationProcessor mockChargeNotificationProcessor;
    @Mock
    private GatewayAccountService mockGatewayAccountService;
    @Mock
    private Charge mockCharge;
    @Mock
    private NotificationRequestItem mockNotificationItem;
    @InjectMocks
    private AdyenCaptureNotificationHandler adyenCaptureNotificationHandler;

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenCaptureNotificationHandler.class);

    private final Date eventDate = Date.from(Instant.parse("2026-05-19T10:15:30Z"));

    @ParameterizedTest
    @CsvSource({
            "true,CAPTURED",
            "false,CAPTURE_ERROR"
    })
    void shouldProcessCaptureForConnectorCharge(boolean success, ChargeStatus expectedStatus) {
        when(mockNotificationItem.isSuccess()).thenReturn(success);
        when(mockNotificationItem.getOriginalReference()).thenReturn("gateway-transaction-id");
        when(mockNotificationItem.getEventDate()).thenReturn(eventDate);
        when(mockCharge.isHistoric()).thenReturn(false);

        adyenCaptureNotificationHandler.process(mockNotificationItem, mockCharge);

        verify(mockChargeNotificationProcessor).invoke(
                "gateway-transaction-id",
                mockCharge,
                expectedStatus,
                ZonedDateTime.ofInstant(eventDate.toInstant(), ZoneId.of("UTC")));
    }

    @ParameterizedTest
    @CsvSource({
            "true,CAPTURED",
            "false,CAPTURE_ERROR"
    })
    void shouldProcessCaptureForHistoricCharge(boolean success, ChargeStatus expectedStatus) {
        GatewayAccountEntity gatewayAccount = GatewayAccountEntityFixture.aGatewayAccountEntity().build();

        when(mockNotificationItem.isSuccess()).thenReturn(success);
        when(mockNotificationItem.getOriginalReference()).thenReturn("gateway-transaction-id");
        when(mockCharge.isHistoric()).thenReturn(true);
        when(mockCharge.getGatewayAccountId()).thenReturn(123L);
        when(mockGatewayAccountService.getGatewayAccount(anyLong())).thenReturn(Optional.of(gatewayAccount));

        adyenCaptureNotificationHandler.process(mockNotificationItem, mockCharge);

        verify(mockChargeNotificationProcessor).processCaptureNotificationForExpungedCharge(
                gatewayAccount,
                "gateway-transaction-id",
                mockCharge,
                expectedStatus);
        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    void shouldLogWhenGatewayAccountMissingForHistoricCharge() {
        when(mockNotificationItem.isSuccess()).thenReturn(true);
        when(mockNotificationItem.getOriginalReference()).thenReturn("gateway-transaction-id");
        when(mockCharge.isHistoric()).thenReturn(true);
        when(mockCharge.getGatewayAccountId()).thenReturn(123L);
        when(mockCharge.getExternalId()).thenReturn("charge-external-id");
        when(mockGatewayAccountService.getGatewayAccount(123L)).thenReturn(Optional.empty());

        adyenCaptureNotificationHandler.process(mockNotificationItem, mockCharge);

        verify(mockChargeNotificationProcessor, never()).processCaptureNotificationForExpungedCharge(any(), any(), any(), any());
        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        assertThat(logs.getEvents(), everyItem(hasProperty("level", is(Level.ERROR))));
        assertThat(logs.getEvents(), hasItems(hasProperty("message", is("GatewayAccount not found for charge"))));
    }

    @Test
    void shouldLogCaptureFailedForUnsuccessfulNotification() {
        when(mockNotificationItem.isSuccess()).thenReturn(false);
        when(mockNotificationItem.getEventCode()).thenReturn(NotificationRequestItem.EVENT_CODE_CAPTURE);
        when(mockNotificationItem.getOriginalReference()).thenReturn("gateway-transaction-id");
        when(mockNotificationItem.getEventDate()).thenReturn(eventDate);
        when(mockCharge.isHistoric()).thenReturn(false);

        adyenCaptureNotificationHandler.process(mockNotificationItem, mockCharge);

        var keyValuePairs = logs.getEvents().stream()
                .flatMap(event -> event.getKeyValuePairs().stream())
                .toList();

        assertThat(logs.getEvents(), hasItems(hasProperty("message", is("Capture failed"))));
        assertThat(keyValuePairs, hasItems(
                new KeyValuePair("gateway_transaction_id", "gateway-transaction-id"),
                new KeyValuePair("event_code", NotificationRequestItem.EVENT_CODE_CAPTURE)));
    }
}
