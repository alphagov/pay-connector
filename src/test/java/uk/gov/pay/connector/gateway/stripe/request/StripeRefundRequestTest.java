package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class StripeRefundRequestTest {
    private final String refundExternalId = "payRefundExternalId";
    private final long refundAmount = 100L;
    private final String stripeChargeId = "stripeChargeId";
    private final String stripeBaseUrl = "stripeUrl";

    private StripeRefundRequest stripeRefundRequest;
    private GatewayAccountEntity gatewayAccount;

    @Mock
    RefundEntity refundEntity;
    @Mock
    Charge charge;

    @Mock
    StripeGatewayConfig stripeGatewayConfig;
    @Mock
    StripeAuthTokens stripeAuthTokens;
    
    @BeforeEach
    public void setUp() {
        gatewayAccount = aGatewayAccountEntity()
                .withGatewayName("stripe")
                .build();
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccount.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        when(refundEntity.getAmount()).thenReturn(refundAmount);
        when(refundEntity.getExternalId()).thenReturn(refundExternalId);

        final RefundGatewayRequest refundGatewayRequest = RefundGatewayRequest.valueOf(charge, refundEntity, gatewayAccount, gatewayAccountCredentialsEntity);

        stripeRefundRequest = StripeRefundRequest.of(refundGatewayRequest, stripeChargeId, stripeGatewayConfig);
    }
    @Test
    void createsCorrectRefundUrl() {
        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
        assertThat(stripeRefundRequest.getUrl(), is(URI.create(stripeBaseUrl + "/v1/refunds")));
    }

    @Test
    void createsCorrectRefundPayload() {
        String payload = stripeRefundRequest.getGatewayOrder().getPayload();
        
        assertThat(payload, containsString("charge=" + stripeChargeId));
        assertThat(payload, containsString("amount=" + refundAmount));
    }
    
    @Test
    void createsCorrectIdempotencyKey() {
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        assertThat(
                stripeRefundRequest.getHeaders().get("Idempotency-Key"), 
                is("refund" + refundExternalId));
    }
}
