package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Before;
import org.mockito.Mock;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;

public abstract class BaseEpdqNotificationServiceTest {
    EpdqNotificationService notificationService;

    @Mock
    protected ChargeService mockChargeService;
    @Mock
    protected GatewayAccountService mockGatewayAccountService;
    @Mock
    protected ChargeNotificationProcessor mockChargeNotificationProcessor;
    @Mock
    protected RefundNotificationProcessor mockRefundNotificationProcessor;
    protected Charge charge;
    protected GatewayAccountEntity gatewayAccountEntity;

    protected final String payId = "transaction-reference";
    final String payIdSub = "pay-id-sub";
    private final String shaPhraseOut = "sha-phrase-out";

    @Before
    public void setup() {
        notificationService = new EpdqNotificationService(
                mockChargeService,
                new EpdqSha512SignatureGenerator(),
                mockChargeNotificationProcessor,
                mockRefundNotificationProcessor,
                mockGatewayAccountService
        );
        gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
        gatewayAccountEntity.setCredentials(ImmutableMap.of(CREDENTIALS_SHA_OUT_PASSPHRASE, shaPhraseOut));
        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build());

//        when(mockGatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(gatewayAccountEntity));
        when(mockChargeService.findByProviderAndTransactionIdFromDbOrLedger(EPDQ.getName(), payId)).thenReturn(Optional.of(charge));
    }

    String notificationPayloadForTransaction(String payId, EpdqNotification.StatusCode statusCode) {
        List<NameValuePair> payloadParameters = buildPayload(payId, statusCode.getCode());
        return notificationPayloadForTransaction(payloadParameters);
    }

    private List<NameValuePair> buildPayload(String payId, String status) {
        return Lists.newArrayList(
                new BasicNameValuePair("STATUS", status),
                new BasicNameValuePair("PAYID", payId),
                new BasicNameValuePair("PAYIDSUB", "pay-id-sub"));
    }

    private String notificationPayloadForTransaction(List<NameValuePair> payloadParameters) {
        String signature = new EpdqSha512SignatureGenerator().sign(payloadParameters, shaPhraseOut);

        payloadParameters.add(new BasicNameValuePair("SHASIGN", signature));
        return URLEncodedUtils.format(payloadParameters, StandardCharsets.UTF_8.toString());
    }
}
