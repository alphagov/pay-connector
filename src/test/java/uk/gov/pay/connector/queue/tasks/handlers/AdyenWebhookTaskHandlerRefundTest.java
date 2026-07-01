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
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import java.io.IOException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_REFUND_SUCCESS_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class AdyenWebhookTaskHandlerRefundTest {

    @Mock
    private RefundDao mockRefundDao;
    @Mock
    private StateTransitionService mockStateTransitionService;
    @Mock
    private ChargeService mockChargeService;
    @Mock
    private ChargeNotificationProcessor mockChargeNotificationProcessor;
    @Mock
    private GatewayAccountService mockGatewayAccountService;
    @Mock
    private AdyenNotificationService mockAdyenNotificationService;
    @Mock
    private UserNotificationService mockUserNotificationService;
    @Mock
    private PaymentProviders mockPaymentproviders;
    @Mock
    private LedgerService mockLedgerService;
    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;
    private AdyenWebhookTaskHandler adyenWebhookTaskHandler;

    @BeforeEach
    void setUp() {
        RefundService refundService = new RefundService(
                mockRefundDao,
                mockPaymentproviders,
                mockUserNotificationService,
                mockStateTransitionService,
                mockLedgerService,
                mockGatewayAccountCredentialsService);
        adyenWebhookTaskHandler = new AdyenWebhookTaskHandler(
                mockChargeService,
                mockChargeNotificationProcessor,
                new RefundNotificationProcessor(refundService, mockUserNotificationService),
                mockGatewayAccountService,
                mockAdyenNotificationService);
    }

    @Test
    void should_transition_refund_in_REFUND_SUBMITTED_state_to_REFUNDED_on_successful_REFUND_event() throws IOException {
        var adyenRefundSuccessNotification = NotificationRequest.fromJson(load(ADYEN_REFUND_SUCCESS_NOTIFICATION));
        given(mockAdyenNotificationService.extractNotificationItems(any()))
                .willReturn(adyenRefundSuccessNotification.getNotificationItems());
        given(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(any(), any()))
                .willReturn(Optional.of(Charge.from(ChargeEntityFixture.aValidChargeEntity().build())));
        given(mockGatewayAccountService.getGatewayAccount(anyLong()))
                .willReturn(Optional.of(GatewayAccountEntityFixture.aGatewayAccountEntity().build()));
        RefundEntity submittedRefundEntity = new RefundEntityFixture()
                .withStatus(RefundStatus.REFUND_SUBMITTED)
                .build();
        given(mockRefundDao.findByChargeExternalIdAndGatewayTransactionId(any(), any()))
                .willReturn(Optional.of(submittedRefundEntity));

        adyenWebhookTaskHandler.processAdyenWebhookNotification("refund-successful-notification");

        then(mockStateTransitionService)
                .should()
                .offerRefundStateTransition(submittedRefundEntity, RefundStatus.REFUNDED);
    }
}
