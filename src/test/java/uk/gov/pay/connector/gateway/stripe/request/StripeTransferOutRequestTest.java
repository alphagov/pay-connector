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
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StripeTransferOutRequestTest {
    
    private final String netTransferAmount = "200";
    private final String chargeExternalId = "payChargeExternalId";
    private final String stripeChargeId = "stripeChargeId";
    private final String stripeBaseUrl = "stripeUrl";
    private final String stripeConnectAccountId = "stripe_account_id";

    private StripeTransferOutRequest stripeTransferOutRequest;

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
        when(charge.getGatewayTransactionId()).thenReturn(stripeChargeId);

        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);

        final CaptureGatewayRequest captureGatewayRequest = CaptureGatewayRequest.valueOf(charge);

        stripeTransferOutRequest = StripeTransferOutRequest.of(netTransferAmount, captureGatewayRequest, stripeGatewayConfig);
    }

    @Test
    public void shouldCreateCorrectUrl() {
        assertThat(stripeTransferOutRequest.getUrl(), is(URI.create(stripeBaseUrl + "/v1/transfers")));
    }

    @Test
    public void shouldCreateCorrectPayload() {
        String payload = stripeTransferOutRequest.getGatewayOrder().getPayload();

        assertThat(payload, containsString("destination=" + stripeConnectAccountId));
        assertThat(payload, containsString("source_transaction=" + stripeChargeId));
        assertThat(payload, containsString("amount=" + netTransferAmount));
        assertThat(payload, containsString("currency=GBP"));
        assertThat(payload, containsString("expand%5B%5D=balance_transaction"));
        assertThat(payload, containsString("expand%5B%5D=destination_payment"));
    }

    @Test
    public void shouldHaveIdempotencyKeySetToChargeExternalId() {
        assertThat(
                stripeTransferOutRequest.getHeaders().get("Idempotency-Key"),
                is(chargeExternalId)
        );
    }
}
