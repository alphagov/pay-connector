package uk.gov.pay.connector.gateway.stripe.request;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StripeRequestTest {
    private StripeRefundRequest stripeRefundRequest;

    @Mock
    RefundEntity refund;
    @Mock
    ChargeEntity charge;
    @Mock
    GatewayAccountEntity gatewayAccount;
    @Mock
    StripeGatewayConfig stripeGatewayConfig;
    @Mock
    StripeAuthTokens stripeAuthTokens;

    @Before
    public void setUp() {
        when(gatewayAccount.getCredentials()).thenReturn(ImmutableMap.of("stripe_account_id", "stripe_connect_account_id"));
        when(charge.getGatewayAccount()).thenReturn(gatewayAccount);
        when(refund.getChargeEntity()).thenReturn(charge);
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        when(stripeAuthTokens.getLive()).thenReturn("live");
        when(stripeAuthTokens.getTest()).thenReturn("test");
        final RefundGatewayRequest refundGatewayRequest = RefundGatewayRequest.valueOf(refund);

        stripeRefundRequest = StripeRefundRequest.of(refundGatewayRequest, "charge_id", stripeGatewayConfig);
    }


    @Test
    public void shouldSetCorrectAuthorizationHeaderForLiveAccount() {
        when(gatewayAccount.isLive()).thenReturn(true);
        assertThat(stripeRefundRequest.getHeaders().get("Authorization"), is("Bearer live"));
    }
    
    @Test
    public void shouldSetCorrectAuthorizationHeaderForTestAccount() {
        when(gatewayAccount.isLive()).thenReturn(false);
        assertThat(stripeRefundRequest.getHeaders().get("Authorization"), is("Bearer test"));
    }
}
