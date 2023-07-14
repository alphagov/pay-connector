package uk.gov.pay.connector.gateway.epdq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.service.StatusFlow.EXPIRE_FLOW;
import static uk.gov.pay.connector.charge.service.StatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.charge.service.StatusFlow.USER_CANCELLATION_FLOW;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_AUTHORISATION_REFUSED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_AUTHORISED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_AUTHORISED_CANCELLED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_DELETION_REFUSED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_PAYMENT_DELETED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_PAYMENT_REQUESTED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_REFUND;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_REFUND_DECLINED_BY_ACQUIRER;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_REFUND_REFUSED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.UNKNOWN;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;

class EpdqNotificationServiceStatusMapperTest extends EpdqNotificationServiceTest {

    @BeforeEach
    void setup() {
        super.setup();
    }

    @Test
    void shouldUpdateChargeToAuthorisationRejected_IfEpdqStatusIs2() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISATION_REFUSED);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verify(mockChargeNotificationProcessor).invoke(payId, charge, AUTHORISATION_REJECTED, null);
    }

    @Test
    void shouldUpdateChargeToAuthorisationSuccess_IfEpdqStatusIs5() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verify(mockChargeNotificationProcessor).invoke(payId, charge, AUTHORISATION_SUCCESS, null);
    }

    @Test
    void shouldUpdateChargeToCaptured_IfEpdqStatusIs9() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_PAYMENT_REQUESTED);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verify(mockChargeNotificationProcessor).invoke(payId, charge, CAPTURED, null);
    }

    @Test
    void shouldUpdateChargeToCaptured_IfEpdqStatusIs9AndChargeIsHistoric() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_PAYMENT_REQUESTED);
        charge = getCharge(true);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verify(mockChargeNotificationProcessor).processCaptureNotificationForExpungedCharge(gatewayAccountEntity, payId, charge, CAPTURED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SYSTEM CANCEL SUBMITTED", "CREATED", "ENTERING CARD DETAILS"})
    void shouldUpdateChargeToSystemCancel_IfEpdqStatusIs6WithRelevantChargeStatus(String status) {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);
        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withStatus(ChargeStatus.fromString(status))
                .withGatewayTransactionId(payId)
                .build());
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verify(mockChargeNotificationProcessor).invoke(payId, charge, SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState(), null);
    }

    @Test
    void shouldUpdateChargeToUserCancel_IfEpdqStatusIs6WithRelevantChargeStatus() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);
        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withStatus(USER_CANCEL_SUBMITTED)
                .build());
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verify(mockChargeNotificationProcessor).invoke(payId, charge, USER_CANCELLATION_FLOW.getSuccessTerminalState(), null);
    }

    @Test
    void shouldUpdateChargeToExpire_IfEpdqStatusIs6WithRelevantChargeStatus() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);
        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withStatus(EXPIRE_CANCEL_SUBMITTED)
                .build());
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verify(mockChargeNotificationProcessor).invoke(payId, charge, EXPIRE_FLOW.getSuccessTerminalState(), null);
    }

    @Test
    void shouldUpdateChargeToSystemCancel_IfEpdqStatusIs6AndChargeStatusIsNotRelevant() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);
        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withStatus(CAPTURED)
                .build());
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    void shouldNotUpdateCharge_IfEpdqStatusIs6AndChargeStatusIsNotPresent() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);
        charge = getCharge(false);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    void shouldNotUpdateCharge_IfEpdqStatusIs6AndChargeIsHistoric() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);
        charge = getCharge(true);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    void shouldNotUpdateCharge_IfEpdqStatusIsUnknown() {
        final String payload = notificationPayloadForTransaction(payId, UNKNOWN);

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    void shouldRefund_IfEpdqStatusIs7() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_PAYMENT_DELETED);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verify(mockRefundNotificationProcessor).invoke(EPDQ, REFUNDED, gatewayAccountEntity, payId + "/" + payIdSub, payId, charge);
    }

    @Test
    void shouldRefund_IfEpdqStatusIs8() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_REFUND);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verify(mockRefundNotificationProcessor).invoke(EPDQ, REFUNDED, gatewayAccountEntity, payId + "/" + payIdSub, payId, charge);
    }

    @Test
    void shouldBeARefundError_IfEpdqStatusIs83() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_REFUND_REFUSED);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verify(mockRefundNotificationProcessor).invoke(EPDQ, REFUND_ERROR, gatewayAccountEntity, payId + "/" + payIdSub, payId, charge);
    }

    @Test
    void shouldBeARefundError_IfEpdqStatusIs73() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_DELETION_REFUSED);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verify(mockRefundNotificationProcessor).invoke(EPDQ, REFUND_ERROR, gatewayAccountEntity, payId + "/" + payIdSub, payId, charge);
    }

    @Test
    void shouldBeARefundError_IfEpdqStatusIs94() {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_REFUND_DECLINED_BY_ACQUIRER);
        setUpGatewayAccountToReturnGatewayAccountEntity(Optional.of(gatewayAccountEntity));
        setUpGatewayAccountCredentialsToReturnGatewayAccountCredentialsEntity(Optional.of(gatewayAccountCredentialsEntity));
        setUpChargeServiceToReturnCharge(Optional.of(charge));

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verify(mockRefundNotificationProcessor).invoke(EPDQ, REFUND_ERROR, gatewayAccountEntity, payId + "/" + payIdSub, payId, charge);
    }

    @Test
    void shouldNotProcessRefund_IfEpdqStatusIsUnknown() {
        final String payload = notificationPayloadForTransaction(payId, UNKNOWN);

        assertTrue(notificationService.handleNotificationFor(payload, FORWARDED_IP_ADDRESSES));
        verifyNoInteractions(mockChargeNotificationProcessor);
    }
    
    private Charge getCharge(boolean isHistoric){
        return new Charge("external-id", 10L, null, null, "transaction-id",
                "credential-external-id", 10L, null, "ref-1", "desc", Instant.now(),
                "test@example.org", 123L, "epdq", isHistoric, "service-id", true, false, AuthorisationMode.WEB,
                null);
    }
}
