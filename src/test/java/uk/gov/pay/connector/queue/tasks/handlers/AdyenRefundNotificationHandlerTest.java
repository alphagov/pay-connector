package uk.gov.pay.connector.queue.tasks.handlers;

import com.adyen.model.notification.NotificationRequestItem;
import io.github.netmikey.logunit.api.LogCapturer;
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
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenRefundNotificationHandler;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdyenRefundNotificationHandlerTest {

    @Mock
    private RefundNotificationProcessor mockRefundNotificationProcessor;
    @Mock
    private GatewayAccountService mockGatewayAccountService;
    @Mock
    private Charge mockCharge;
    @Mock
    private NotificationRequestItem mockNotificationItem;
    @InjectMocks
    private AdyenRefundNotificationHandler adyenRefundNotificationHandler;

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenRefundNotificationHandler.class);

    @ParameterizedTest
    @CsvSource({
            "true,REFUNDED",
            "false,REFUND_ERROR"
    })
    void should_delegate_refund_notification_with_expected_status(boolean success, RefundStatus expectedStatus) {
        GatewayAccountEntity gatewayAccount = GatewayAccountEntityFixture.aGatewayAccountEntity().build();

        when(mockNotificationItem.isSuccess()).thenReturn(success);
        when(mockNotificationItem.getPspReference()).thenReturn("adyen-refund-id-1");
        when(mockNotificationItem.getOriginalReference()).thenReturn("refund-123");
        when(mockCharge.getGatewayAccountId()).thenReturn(1L);
        when(mockCharge.getExternalId()).thenReturn("charge-123");
        when(mockGatewayAccountService.getGatewayAccount(anyLong())).thenReturn(Optional.of(gatewayAccount));

        adyenRefundNotificationHandler.process(mockNotificationItem, mockCharge);

        verify(mockRefundNotificationProcessor).invoke(
                PaymentGatewayName.ADYEN,
                expectedStatus,
                gatewayAccount,
                "adyen-refund-id-1",
                "refund-123",
                mockCharge);
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void should_log_warning_and_not_delegate_when_gateway_account_is_missing(boolean success) {
        when(mockNotificationItem.isSuccess()).thenReturn(success);
        when(mockNotificationItem.getOriginalReference()).thenReturn("refund-123");
        when(mockCharge.getGatewayAccountId()).thenReturn(1L);
        when(mockCharge.getExternalId()).thenReturn("charge-123");
        when(mockGatewayAccountService.getGatewayAccount(anyLong())).thenReturn(Optional.empty());

        adyenRefundNotificationHandler.process(mockNotificationItem, mockCharge);

        verifyNoInteractions(mockRefundNotificationProcessor);
        assertThat(logs.getEvents(), everyItem(hasProperty("level", is(Level.WARN))));
        assertThat(logs.getEvents(), everyItem(hasProperty("message", is("GatewayAccount not found for refund notification"))));
        var keyValuePairs = logs.getEvents().stream()
                .flatMap(event -> event.getKeyValuePairs().stream())
                .toList();
        assertThat(keyValuePairs, hasItems(
                new KeyValuePair("payment_external_id", "charge-123"),
                new KeyValuePair("gateway_transaction_id", "refund-123")));
    }
}
