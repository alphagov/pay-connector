package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Before;
import org.mockito.Mock;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;

public abstract class BaseEpdqNotificationServiceTest {
    EpdqNotificationService notificationService;

    @Mock
    protected ChargeDao mockChargeDao;
    @Mock
    private RefundDao mockRefundDao;
    @Mock
    protected UserNotificationService mockUserNotificationService;
    @Mock
    protected ChargeNotificationProcessor mockChargeNotificationProcessor;

    RefundNotificationProcessor mockRefundNotificationProcessor;
    @Mock
    protected ChargeEntity mockCharge;
    @Mock
    private RefundEntity mockRefund;
    @Mock
    protected GatewayAccountEntity mockGatewayAccountEntity;

    protected final String payId = "transaction-reference";
    final String payIdSub = "pay-id-sub";
    private final String shaPhraseOut = "sha-phrase-out";

    @Before
    public void setup() {
        mockRefundNotificationProcessor = spy(new RefundNotificationProcessor(mockRefundDao, mockUserNotificationService));
        notificationService = new EpdqNotificationService(
                mockChargeDao,
                new EpdqSha512SignatureGenerator(),
                mockChargeNotificationProcessor,
                mockRefundNotificationProcessor
        );
        when(mockCharge.getStatus()).thenReturn(CAPTURE_APPROVED.getValue());
        when(mockCharge.getGatewayAccount()).thenReturn(mockGatewayAccountEntity);
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(ImmutableMap.of(CREDENTIALS_SHA_OUT_PASSPHRASE, shaPhraseOut));

        when(mockChargeDao.findByProviderAndTransactionId(EPDQ.getName(), payId)).thenReturn(Optional.of(mockCharge));
        when(mockRefundDao.findByProviderAndReference(EPDQ.getName(), payId + "/" + payIdSub)).thenReturn(Optional.of(mockRefund));
        when(mockRefund.getChargeEntity()).thenReturn(mockCharge);
        when(mockCharge.getGatewayAccount()).thenReturn(mockGatewayAccountEntity);
    }

    protected String notificationPayloadForTransaction(String payId, EpdqNotification.StatusCode statusCode) {
        List<NameValuePair> payloadParameters = buildPayload(payId, statusCode.getCode());
        return notificationPayloadForTransaction(payloadParameters);
    }

    private List<NameValuePair> buildPayload(String payId, String status) {
        return Lists.newArrayList(
                new BasicNameValuePair("STATUS", status),
                new BasicNameValuePair("PAYID", payId),
                new BasicNameValuePair("PAYIDSUB", "pay-id-sub"));
    }

    protected String notificationPayloadForTransaction(List<NameValuePair> payloadParameters) {
        String signature = new EpdqSha512SignatureGenerator().sign(payloadParameters, shaPhraseOut);

        payloadParameters.add(new BasicNameValuePair("SHASIGN", signature));
        return URLEncodedUtils.format(payloadParameters, StandardCharsets.UTF_8.toString());
    }
}
