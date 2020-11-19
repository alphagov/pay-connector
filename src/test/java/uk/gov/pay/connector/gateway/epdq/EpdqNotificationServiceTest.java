package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
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

@ExtendWith(MockitoExtension.class)
class EpdqNotificationServiceTest extends BaseEpdqNotificationServiceTest {

    @BeforeEach
    void setup() {
        super.setup();
    }

    @Test
    void givenAChargeCapturedNotification_chargeNotificationProcessorInvokedWithNotificationAndCharge() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_PAYMENT_REQUESTED);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockRefundNotificationProcessor);
        verify(mockChargeNotificationProcessor).invoke(payId, charge, CAPTURED, null);
    }

    @Test
    void givenChargeCapturedNotification_chargeNotificationProcessorShouldBeInvokedIfChargeIsHistoric() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_PAYMENT_REQUESTED);
        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build());
        charge.setHistoric(true);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockRefundNotificationProcessor);
        verify(mockChargeNotificationProcessor).processCaptureNotificationForExpungedCharge(gatewayAccountEntity, payId, charge, CAPTURED);
    }

    @Test
    void givenARefundNotification_refundNotificationProcessorInvokedWithNotificationAndCharge() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_REFUND);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verify(mockRefundNotificationProcessor).invoke(EPDQ, RefundStatus.REFUNDED, gatewayAccountEntity, 
                payId + "/" + payIdSub, payId, charge);
    }

    @Test
    void ifChargeNotFound_shouldNotInvokeChargeOrRefundNotificationProcessor() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_REFUND);
        setUpChargeServiceToReturnCharge(Optional.empty());

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void ifGatewayAccountNotFound_shouldNotInvokeChargeOrRefundNotificationProcessor() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_REFUND);
        setUpChargeServiceToReturnCharge(Optional.of(charge));
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.empty());

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void ifTransactionIdEmpty_shouldNotInvokeChargeOrRefundNotificationProcessor() {
        final String payload = notificationPayloadForTransaction(
                null,
                EPDQ_REFUND);

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void ifPayloadNotValidXml_shouldIgnoreNotification() {
        final String payload = "not_valid";

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void shouldIgnoreNotificationWhenStatusIsUnknown() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EpdqNotification.StatusCode.UNKNOWN);

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void shouldNotUpdateIfShaPhraseExpectedIsIncorrect() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_PAYMENT_REQUESTED);
        gatewayAccountEntity.setCredentials(ImmutableMap.of(CREDENTIALS_SHA_OUT_PASSPHRASE, "sha-phrase-out-expected"));

        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    protected void setUpGatewayAccountToReturnGatewayAccountEntity(Optional<GatewayAccountEntity> gatewayAccountEntity) {
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(gatewayAccountEntity);
    }

    protected void setUpChargeServiceToReturnCharge(Optional<Charge> charge) {
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(charge);
    }
}
