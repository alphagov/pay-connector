package uk.gov.pay.connector.gateway.stripe.request;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import java.net.URI;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StripeRefundRequestTest {
    private final String refundExternalId = "payRefundExternalId";
    private final long refundAmount = 100L;
    private final String stripeChargeId = "stripeChargeId";
    private final String stripeBaseUrl = "stripeUrl";

    private StripeRefundRequest stripeRefundRequest;

    @Mock
    RefundEntity refundEntity;
    @Mock
    Charge charge;
    @Mock
    GatewayAccountEntity gatewayAccount;
    @Mock
    StripeGatewayConfig stripeGatewayConfig;
    @Mock
    StripeAuthTokens stripeAuthTokens;
    @Mock
    ChargeDao chargeDao;
    
    @Before
    public void setUp() {
        when(gatewayAccount.getCredentials()).thenReturn(ImmutableMap.of("stripe_account_id", "stripe_account_id"));

        when(refundEntity.getAmount()).thenReturn(refundAmount);
        when(refundEntity.getExternalId()).thenReturn(refundExternalId);

        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);

        final RefundGatewayRequest refundGatewayRequest = RefundGatewayRequest.valueOf(charge, refundEntity, gatewayAccount);

        stripeRefundRequest = StripeRefundRequest.of(refundGatewayRequest, stripeChargeId, stripeGatewayConfig);
    }
    @Test
    public void createsCorrectRefundUrl() {
        assertThat(stripeRefundRequest.getUrl(), is(URI.create(stripeBaseUrl + "/v1/refunds")));
    }

    @Test
    public void createsCorrectRefundPayload() {
        String payload = stripeRefundRequest.getGatewayOrder().getPayload();
        
        assertThat(payload, containsString("charge=" + stripeChargeId));
        assertThat(payload, containsString("amount=" + refundAmount));
    }
    
    @Test
    public void createsCorrectIdempotencyKey() {
        assertThat(
                stripeRefundRequest.getHeaders().get("Idempotency-Key"), 
                is("refund" + refundExternalId));
    }
}
