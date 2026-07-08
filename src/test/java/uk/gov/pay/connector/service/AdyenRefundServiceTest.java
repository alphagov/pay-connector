package uk.gov.pay.connector.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.aValidRefundEntity;

@ExtendWith(MockitoExtension.class)
class AdyenRefundServiceTest {

    @Mock
    private RefundDao mockRefundDao;
    @Mock
    private PaymentProviders mockProviders;
    @Mock
    private UserNotificationService mockUserNotificationService;
    @Mock
    private StateTransitionService mockStateTransitionService;
    @Mock
    private LedgerService mockLedgerService;
    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;

    private RefundService refundService;
    private ChargeEntity chargeEntity;
    private final List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntityList = new ArrayList<>();
    private final Long accountId = 2L;
    private final GatewayAccountEntity account = aGatewayAccountEntity()
            .withId(accountId)
            .withType(TEST)
            .withGatewayAccountCredentials(gatewayAccountCredentialsEntityList)
            .build();

    @BeforeEach
    void setUp() {
        refundService = new RefundService(
                mockRefundDao,
                mockProviders,
                mockUserNotificationService,
                mockStateTransitionService,
                mockLedgerService,
                mockGatewayAccountCredentialsService);
    }

    @Test
    void shouldTransitionRefundFromRefundErrorToRefundedForAdyenWebhook() {
        chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(account)
                .withTransactionId("transaction-id")
                .withExternalId("charge-id")
                .withStatus(uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED)
                .withPaymentProvider(PaymentGatewayName.ADYEN.getName())
                .build();

        RefundEntity refundEntity = aValidRefundEntity()
                .withChargeExternalId("charge-id")
                .withStatus(RefundStatus.REFUND_ERROR)
                .build();

        refundService.transitionRefundStateForAdyenWebhook(refundEntity, account, RefundStatus.REFUNDED, Charge.from(chargeEntity));

        assertThat(refundEntity.getStatus(), is(RefundStatus.REFUNDED));
        verify(mockStateTransitionService).offerRefundStateTransition(refundEntity, RefundStatus.REFUNDED);
    }
}
