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
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StripeCaptureRequestTest {
    private final String chargeExternalId = "payChargeExternalId";
    private final String stripeChargeId = "stripeChargeId";
    private final String stripeBaseUrl = "stripeUrl";
    private final String stripeLiveApiToken = "stripe_live_api_token";
    private final long gatewayAccountId = 123L;

    private CaptureGatewayRequest captureGatewayRequest;
    private StripeCaptureRequest stripeCaptureRequest;

    @Mock
    ChargeEntity charge;
    @Mock
    GatewayAccountEntity gatewayAccount;
    @Mock
    StripeAuthTokens stripeAuthTokens;
    @Mock
    StripeGatewayConfig stripeGatewayConfig;

    @Before
    public void setUp() {
        when(gatewayAccount.getCredentials()).thenReturn(ImmutableMap.of("stripe_account_id", "stripe_account_id"));
        when(gatewayAccount.getId()).thenReturn(gatewayAccountId);
        when(gatewayAccount.isLive()).thenReturn(true);

        when(charge.getGatewayTransactionId()).thenReturn(stripeChargeId);
        when(charge.getGatewayAccount()).thenReturn(gatewayAccount);
        when(charge.getExternalId()).thenReturn(chargeExternalId);

        captureGatewayRequest = CaptureGatewayRequest.valueOf(charge);

        when(stripeAuthTokens.getLive()).thenReturn(stripeLiveApiToken);
        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);

        stripeCaptureRequest = StripeCaptureRequest.of(captureGatewayRequest, stripeGatewayConfig);
    }

    @Test
    public void shouldCreateCorrectCaptureUrl() {
        assertThat(
                stripeCaptureRequest.getUrl(),
                is(URI.create(stripeBaseUrl + "/v1/charges/" + stripeChargeId + "/capture"))
        );
    }

    @Test
    public void shouldCreateCorrectCapturePayload() {
        assertThat(
                stripeCaptureRequest.getGatewayOrder().getPayload(),
                containsString("expand%5B%5D=balance_transaction")
        );
    }

    @Test
    public void shouldSetGatewayOrderToBeOfTypeCapture() {
        assertThat(
                stripeCaptureRequest.getGatewayOrder().getOrderRequestType(),
                is(OrderRequestType.CAPTURE)
        );
    }

    @Test
    public void shouldCreateCorrectCaptureHeaders() {
        assertThat(
                stripeCaptureRequest.getHeaders().get("Idempotency-Key"),
                is("capture" + chargeExternalId)
        );

        assertThat(
                stripeCaptureRequest.getHeaders().get("Authorization"),
                is("Bearer " + stripeLiveApiToken)
        );
    }
    
    @Test
    public void shouldContainCorrectGatewayAccount() {
        assertThat(
                stripeCaptureRequest.getGatewayAccount().getId(),
                is(gatewayAccountId)
        );
    }
}
