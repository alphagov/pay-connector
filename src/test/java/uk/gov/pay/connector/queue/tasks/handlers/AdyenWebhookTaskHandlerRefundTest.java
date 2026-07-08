package uk.gov.pay.connector.queue.tasks.handlers;

import com.adyen.model.notification.NotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenCancellationNotificationHandler;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenRefundNotificationHandler;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenWebhookTaskHandler;

import java.io.IOException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_REFUND_FAILURE_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_REFUND_SUCCESS_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class AdyenWebhookTaskHandlerRefundTest {

    @Mock
    private ChargeService mockChargeService;
    @Mock
    private ChargeNotificationProcessor mockChargeNotificationProcessor;
    @Mock
    private GatewayAccountService mockGatewayAccountService;
    @Mock
    private AdyenNotificationService mockAdyenNotificationService;
    @Mock
    private AdyenCancellationNotificationHandler mockAdyenCancellationNotificationHandler;
    @Mock
    private AdyenRefundNotificationHandler mockAdyenRefundNotificationHandler;

    private AdyenWebhookTaskHandler adyenWebhookTaskHandler;

    @BeforeEach
    void setUp() {
        adyenWebhookTaskHandler = new AdyenWebhookTaskHandler(
                mockChargeService,
                mockChargeNotificationProcessor,
                mockGatewayAccountService,
                mockAdyenNotificationService,
                mockAdyenCancellationNotificationHandler,
                mockAdyenRefundNotificationHandler);
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
