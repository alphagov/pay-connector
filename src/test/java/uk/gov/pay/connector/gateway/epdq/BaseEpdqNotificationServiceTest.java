package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.processor.ChargeNotificationProcessor;
import uk.gov.pay.connector.gateway.processor.RefundNotificationProcessor;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.util.CidrUtils;
import uk.gov.pay.connector.util.IpAddressMatcher;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;

abstract class BaseEpdqNotificationServiceTest {
    private static final Set<String> ALLOWED_IP_ADDRESSES = CidrUtils.getIpAddresses(List.of("102.22.31.0/24", "9.9.9.9/32"));

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

    @BeforeEach
    void setup() {
        notificationService = new EpdqNotificationService(
                mockChargeService,
                new EpdqSha512SignatureGenerator(),
                mockChargeNotificationProcessor,
                mockRefundNotificationProcessor,
                mockGatewayAccountService,
                new IpAddressMatcher(new InetAddressValidator()),
                ALLOWED_IP_ADDRESSES
        );
        gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(EPDQ.getName())
                .withCredentials(ImmutableMap.of(CREDENTIALS_SHA_OUT_PASSPHRASE, shaPhraseOut))
                .withId(1L)
                .build();

        charge = Charge.from(ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(EPDQ.getName())
                .build());
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
