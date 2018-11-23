package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_PAYMENT_REQUESTED;
import static uk.gov.pay.connector.gateway.epdq.EpdqNotification.StatusCode.EPDQ_REFUND;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;

@RunWith(MockitoJUnitRunner.class)
public class EpdqNotificationServiceTest extends BaseEpdqNotificationServiceTest{
   
    @Before
    public void setup() {
        super.setup();
    }

    @Test
    public void givenAChargeCapturedNotification_chargeNotificationProcessorInvokedWithNotificationAndCharge() {

        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_PAYMENT_REQUESTED);

        notificationService.handleNotificationFor(payload);

        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockChargeNotificationProcessor).invoke(payId, mockCharge, CAPTURED, null);
    }

    @Test
    public void givenARefundNotification_refundNotificationProcessorInvokedWithNotificationAndCharge() {

        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_REFUND);

        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor).invoke(EPDQ, RefundStatus.REFUNDED, payId + "/" + payIdSub, payId);
    }

    @Test
    public void ifChargeNotFound_shouldNotInvokeChargeOrRefundNotificationProcessor() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EPDQ_REFUND);


        when(mockChargeDao.findByProviderAndTransactionId(EPDQ.getName(), payId)).thenReturn(Optional.empty());

        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void ifTransactionIdEmpty_shouldNotInvokeChargeOrRefundNotificationProcessor() {
        final String payload = notificationPayloadForTransaction(
                null,
                EPDQ_REFUND);
        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(anyString(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void ifPayloadNotValidXml_shouldIgnoreNotification() {
        String payload = "not_valid";

        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(any(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldIgnoreNotificationWhenStatusIsUnknown() {
        final String payload = notificationPayloadForTransaction(
                payId,
                EpdqNotification.StatusCode.UNKNOWN);

        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(anyString(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }

    @Test
    public void shouldNotUpdateIfShaPhraseExpectedIsIncorrect() {

        when(mockGatewayAccountEntity.getCredentials())
                .thenReturn(ImmutableMap.of(CREDENTIALS_SHA_OUT_PASSPHRASE, "sha-phrase-out-expected"));

        final String payload = notificationPayloadForTransaction(payId, EPDQ_PAYMENT_REQUESTED);

        notificationService.handleNotificationFor(payload);

        verify(mockChargeNotificationProcessor, never()).invoke(anyString(), any(), any(), any());
        verify(mockRefundNotificationProcessor, never()).invoke(any(), any(), any(), any());
    }
}
