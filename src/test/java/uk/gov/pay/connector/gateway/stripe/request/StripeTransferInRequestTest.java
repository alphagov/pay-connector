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

import java.net.URI;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StripeTransferInRequestTest {
    private final String refundExternalId = "payRefundExternalId";
    private final String chargeExternalId = "payChargeExternalId";
    private final String stripeChargeId = "stripeChargeId";
    private final long refundAmount = 100L;
    private final String stripeBaseUrl = "stripeUrl";
    private final String stripePlatformAccountId = "stripePlatformAccountId";
    private final String stripeConnectAccountId = "stripe_account_id"; 

    private StripeTransferInRequest stripeTransferInRequest;
    
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
        when(gatewayAccount.getCredentials()).thenReturn(ImmutableMap.of("stripe_account_id", stripeConnectAccountId));

        when(charge.getGatewayAccount()).thenReturn(gatewayAccount);
        when(charge.getExternalId()).thenReturn(chargeExternalId);

        when(refund.getAmount()).thenReturn(refundAmount);
        when(refund.getExternalId()).thenReturn(refundExternalId);
        when(refund.getChargeEntity()).thenReturn(charge);

        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        when(stripeGatewayConfig.getPlatformAccountId()).thenReturn(stripePlatformAccountId);

        final RefundGatewayRequest refundGatewayRequest = RefundGatewayRequest.valueOf(refund);

        stripeTransferInRequest = StripeTransferInRequest.of(refundGatewayRequest, stripeChargeId, stripeGatewayConfig);
    }

    @Test
    public void shouldCreateCorrectUrl() {
        assertThat(stripeTransferInRequest.getUrl(), is(URI.create(stripeBaseUrl + "/v1/transfers")));
    }

    @Test
    public void shouldCreateCorrectPayload() {
        String payload = stripeTransferInRequest.getGatewayOrder().getPayload();
        
        assertThat(payload, containsString("destination=" + stripePlatformAccountId));
        assertThat(payload, containsString("amount=" + refundAmount));
        assertThat(payload, containsString("transfer_group=" + chargeExternalId));
        assertThat(payload, containsString("expand%5B%5D=balance_transaction"));
        assertThat(payload, containsString("expand%5B%5D=destination_payment"));
        assertThat(payload, containsString("currency=GBP"));
        assertThat(payload, containsString("metadata%5Bstripe_charge_id%5D=" + stripeChargeId));
    }
    
    @Test
    public void shouldHaveStripeAccountHeaderSetToStripeConnectAccountId() {
        assertThat(
                stripeTransferInRequest.getHeaders().get("Stripe-Account"),
                is(stripeConnectAccountId)
        );
    }

    @Test
    public void shouldHaveIdempotencyKeySetToRefundExternalId() {
        assertThat(
                stripeTransferInRequest.getHeaders().get("Idempotency-Key"),
                is("transfer_in" + refundExternalId)
        );
    }
}
