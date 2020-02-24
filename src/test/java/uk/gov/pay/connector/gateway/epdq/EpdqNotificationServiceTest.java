package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_PAYMENT_REQUESTED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_REFUND;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;

@RunWith(MockitoJUnitRunner.class)
public class EpdqNotificationServiceTest extends BaseEpdqNotificationServiceTest {

    @Before
    public void setup() {
        super.setup();
    }

    @Test
    public void givenAChargeCapturedNotification_chargeNotificationProcessorInvokedWithNotificationAndCharge() {

        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_PAYMENT_REQUESTED);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockRefundNotificationProcessor);
        verify(mockChargeNotificationProcessor).invoke(payId, charge, CAPTURED, null);
    }

    @Test
    public void givenAChargeCapturedNotification_chargeNotificationProcessorShouldNotBeInvokedIfChargeIsHistoric() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_PAYMENT_REQUESTED);
        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build());
        charge.setHistoric(true);

        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockRefundNotificationProcessor);
        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    public void givenARefundNotification_refundNotificationProcessorInvokedWithNotificationAndCharge() {

        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_REFUND);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verify(mockRefundNotificationProcessor).invoke(EPDQ, RefundStatus.REFUNDED, gatewayAccountEntity, 
                payId + "/" + payIdSub, payId, charge);
    }

    @Test
    public void ifChargeNotFound_shouldNotInvokeChargeOrRefundNotificationProcessor() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_REFUND);


        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.empty());

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    public void ifGatewayAccountNotFound_shouldNotInvokeChargeOrRefundNotificationProcessor() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_REFUND);

        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.empty());

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    public void ifTransactionIdEmpty_shouldNotInvokeChargeOrRefundNotificationProcessor() {
        final String payload = notificationPayloadForTransaction(
                null,
                EPDQ_REFUND);
        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    public void ifPayloadNotValidXml_shouldIgnoreNotification() {
        String payload = "not_valid";

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    public void shouldIgnoreNotificationWhenStatusIsUnknown() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EpdqNotification.StatusCode.UNKNOWN);

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    public void shouldNotUpdateIfShaPhraseExpectedIsIncorrect() {
        gatewayAccountEntity.setCredentials(ImmutableMap.of(CREDENTIALS_SHA_OUT_PASSPHRASE, "sha-phrase-out-expected"));

        final String payload = notificationPayloadForTransaction(payId, EPDQ_PAYMENT_REQUESTED);

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }
}
