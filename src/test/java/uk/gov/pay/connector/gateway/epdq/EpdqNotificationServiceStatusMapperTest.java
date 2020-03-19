package uk.gov.pay.connector.gateway.epdq;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.queue.QueueException;

import java.util.Optional;

import static java.time.ZonedDateTime.now;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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

@RunWith(JUnitParamsRunner.class)
public class EpdqNotificationServiceStatusMapperTest extends EpdqNotificationServiceTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Before
    public void setup() {
        super.setup();
    }

    @Test
    public void shouldUpdateChargeToAuthorisationRejected_IfEpdqStatusIs2() throws QueueException {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISATION_REFUSED);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor).invoke(payId, charge, AUTHORISATION_REJECTED, null);
    }

    @Test
    public void shouldUpdateChargeToAuthorisationSuccess_IfEpdqStatusIs5() throws QueueException {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor).invoke(payId, charge, AUTHORISATION_SUCCESS, null);
    }

    @Test
    public void shouldUpdateChargeToCaptured_IfEpdqStatusIs9() throws QueueException {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_PAYMENT_REQUESTED);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor).invoke(payId, charge, CAPTURED, null);
    }

    @Test
    public void shouldUpdateChargeToCaptured_IfEpdqStatusIs9AndChargeIsHistoric() throws QueueException {
        charge = getCharge(true);
        final String payload = notificationPayloadForTransaction(payId, EPDQ_PAYMENT_REQUESTED);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
        notificationService.handleNotificationFor(payload);
        

        verify(mockChargeNotificationProcessor).processCaptureNotificationForExpungedCharge(gatewayAccountEntity, payId, charge, CAPTURED);
    }

    @Test
    @Parameters({"SYSTEM CANCEL SUBMITTED", "CREATED", "ENTERING CARD DETAILS"})
    public void shouldUpdateChargeToSystemCancel_IfEpdqStatusIs6WithRelevantChargeStatus(String status) throws QueueException {
        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withStatus(ChargeStatus.fromString(status))
                .withGatewayTransactionId(payId)
                .build());
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger("epdq", payId))
                .thenReturn(Optional.of(charge));

        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);
        notificationService.handleNotificationFor(payload);
        verify(mockChargeNotificationProcessor).invoke(payId, charge, SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState(), null);
    }

    @Test
    public void shouldUpdateChargeToUserCancel_IfEpdqStatusIs6WithRelevantChargeStatus() throws QueueException {
        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withStatus(USER_CANCEL_SUBMITTED)
                .build());
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(any(), any())).thenReturn(Optional.of(charge));
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);

        notificationService.handleNotificationFor(payload);
        verify(mockChargeNotificationProcessor).invoke(payId, charge, USER_CANCELLATION_FLOW.getSuccessTerminalState(), null);
    }

    @Test
    public void shouldUpdateChargeToExpire_IfEpdqStatusIs6WithRelevantChargeStatus() throws QueueException {
        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withStatus(EXPIRE_CANCEL_SUBMITTED)
                .build());
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(any(), any())).thenReturn(Optional.of(charge));

        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);

        notificationService.handleNotificationFor(payload);
        verify(mockChargeNotificationProcessor).invoke(payId, charge, EXPIRE_FLOW.getSuccessTerminalState(), null);
    }

    @Test
    public void shouldUpdateChargeToSystemCancel_IfEpdqStatusIs6AndChargeStatusIsNotRelevant() throws QueueException {
        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withStatus(CAPTURED)
                .build());
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(any(), any())).thenReturn(Optional.of(charge));

        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);

        notificationService.handleNotificationFor(payload);
        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    public void shouldNotUpdateCharge_IfEpdqStatusIs6AndChargeStatusIsNotPresent() throws QueueException {
        charge = getCharge(false);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));

        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(any(), any())).thenReturn(Optional.of(charge));

        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);

        notificationService.handleNotificationFor(payload);
        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    public void shouldNotUpdateCharge_IfEpdqStatusIs6AndChargeIsHistoric() throws QueueException {
        charge = getCharge(true);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(any(), any())).thenReturn(Optional.of(charge));

        final String payload = notificationPayloadForTransaction(payId, EPDQ_AUTHORISED_CANCELLED);

        notificationService.handleNotificationFor(payload);
        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    public void shouldNotUpdateCharge_IfEpdqStatusIsUnknown() throws QueueException {
        final String payload = notificationPayloadForTransaction(payId, UNKNOWN);
        notificationService.handleNotificationFor(payload);

        verifyNoInteractions(mockChargeNotificationProcessor);
    }

    @Test
    public void shouldRefund_IfEpdqStatusIs7() throws QueueException {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_PAYMENT_DELETED);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
        notificationService.handleNotificationFor(payload);
        verify(mockRefundNotificationProcessor).invoke(EPDQ, REFUNDED, gatewayAccountEntity, payId + "/" + payIdSub, payId, charge);
    }

    @Test
    public void shouldRefund_IfEpdqStatusIs8() throws QueueException {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_REFUND);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
        notificationService.handleNotificationFor(payload);
        verify(mockRefundNotificationProcessor).invoke(EPDQ, REFUNDED, gatewayAccountEntity, payId + "/" + payIdSub, payId, charge);
    }

    @Test
    public void shouldBeARefundError_IfEpdqStatusIs83() throws QueueException {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_REFUND_REFUSED);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
        notificationService.handleNotificationFor(payload);
        verify(mockRefundNotificationProcessor).invoke(EPDQ, REFUND_ERROR, gatewayAccountEntity, payId + "/" + payIdSub, payId, charge);
    }

    @Test
    public void shouldBeARefundError_IfEpdqStatusIs73() throws QueueException {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_DELETION_REFUSED);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
        notificationService.handleNotificationFor(payload);
        verify(mockRefundNotificationProcessor).invoke(EPDQ, REFUND_ERROR, gatewayAccountEntity, payId + "/" + payIdSub, payId, charge);
    }

    @Test
    public void shouldBeARefundError_IfEpdqStatusIs94() throws QueueException {
        final String payload = notificationPayloadForTransaction(payId, EPDQ_REFUND_DECLINED_BY_ACQUIRER);
        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
        notificationService.handleNotificationFor(payload);
        verify(mockRefundNotificationProcessor).invoke(EPDQ, REFUND_ERROR, gatewayAccountEntity, payId + "/" + payIdSub, payId, charge);
    }

    @Test
    public void shouldNotProcessRefund_IfEpdqStatusIsUnknown() throws QueueException {
        final String payload = notificationPayloadForTransaction(payId, UNKNOWN);

        notificationService.handleNotificationFor(payload);
        verifyNoInteractions(mockChargeNotificationProcessor);
    }
    
    private Charge getCharge(boolean isHistoric){
        return new Charge("external-id", 10L, null, null, "transaction-id",
                10L, null, "ref-1", "desc", now(),
                "test@example.org", 123L, "epdq", isHistoric);
    }
}
