package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_PAYMENT_REQUESTED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_REFUND;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class EpdqNotificationServiceTest extends BaseEpdqNotificationServiceTest {
    protected static final String FORWARDED_IP_ADDRESSES = "102.108.0.6, 102.22.31.106";

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
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
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
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verifyNoInteractions(mockRefundNotificationProcessor);
        verify(mockChargeNotificationProcessor).processCaptureNotificationForExpungedCharge(gatewayAccountEntity, payId, charge, CAPTURED);
    }

    @Test
    void givenARefundNotification_refundNotificationProcessorInvokedWithNotificationAndCharge() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_REFUND);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
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

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
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

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void ifGatewayAccountCredentialsNotFound_shouldNotInvokeChargeOrRefundNotificationProcessor() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_REFUND);
        setUpChargeServiceToReturnCharge(Optional.of(charge));
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.empty());

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void ifTransactionIdEmpty_shouldNotInvokeChargeOrRefundNotificationProcessor() {
        final String payload = notificationPayloadForTransaction(
                null,
                EPDQ_REFUND);

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void ifPayloadNotValidXml_shouldIgnoreNotification() {
        final String payload = "not_valid";

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void shouldIgnoreNotificationWhenStatusIsUnknown() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EpdqNotification.StatusCode.UNKNOWN);

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void shouldNotUpdateIfShaPhraseExpectedIsIncorrect() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_PAYMENT_REQUESTED);
        GatewayAccountCredentialsEntity creds = aGatewayAccountCredentialsEntity()
                .withCredentials(ImmutableMap.of(CREDENTIALS_SHA_OUT_PASSPHRASE, "sha-phrase-out-expected"))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(EPDQ.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(creds));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verifyNoInteractions(mockChargeNotificationProcessor);
        verifyNoInteractions(mockRefundNotificationProcessor);
    }

    @Test
    void shouldReturnFalseWhenForwardedIpAddressIsNotInAllowedIpAddresses() {
        final String forwardedIpAddresses = "1.1.1.1, 102.108.0.6";
        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_PAYMENT_REQUESTED);

        assertFalse(notificationService.handleNotificationFor(payload, forwardedIpAddresses));
    }

    protected void setUpGatewayAccountToReturnGatewayAccountEntity(Optional<GatewayAccountEntity> gatewayAccountEntity) {
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(gatewayAccountEntity);
    }

    protected void setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntity) {
        when(mockGatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity)).thenReturn(gatewayAccountCredentialsEntity);
    }

    protected void setUpChargeServiceToReturnCharge(Optional<Charge> charge) {
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(charge);
    }
}
