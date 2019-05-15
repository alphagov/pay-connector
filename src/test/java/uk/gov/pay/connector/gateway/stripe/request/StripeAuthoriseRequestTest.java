package uk.gov.pay.connector.gateway.stripe.request;

import com.google.common.collect.ImmutableMap;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StripeAuthoriseRequestTest {
    private final String stripeSourceId = "stripeSourceId";
    private final String stripeConnectAccountId = "stripeConnectAccountId";
    private final String description = "chargeDescription";
    private final String chargeExternalId = "payChargeExternalId";
    private final String stripeBaseUrl = "stripeUrl";
    private final long amount = 123L;

    private CardAuthorisationGatewayRequest authorisationGatewayRequest;
    private StripeAuthoriseRequest stripeAuthoriseRequest;

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
        when(gatewayAccount.getCredentials()).thenReturn(ImmutableMap.of("stripe_account_id", stripeConnectAccountId));

        when(charge.getGatewayAccount()).thenReturn(gatewayAccount);
        when(charge.getExternalId()).thenReturn(chargeExternalId);
        when(charge.getDescription()).thenReturn(description);
        when(charge.getAmount()).thenReturn(amount);

        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        
        authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, new AuthCardDetails());

        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);

        stripeAuthoriseRequest = StripeAuthoriseRequest.of(stripeSourceId, authorisationGatewayRequest, stripeGatewayConfig);
    }

    @Test
    public void shouldCreateAuthoriseCaptureUrl() {
        assertThat(
                stripeAuthoriseRequest.getUrl(),
                is(URI.create(stripeBaseUrl + "/v1/charges"))
        );
    }

    @Test
    public void shouldCreateCorrectPayload() {
        String payload = stripeAuthoriseRequest.getGatewayOrder().getPayload();

        Assert.assertThat(payload, CoreMatchers.containsString("amount=" + amount));
        Assert.assertThat(payload, CoreMatchers.containsString("transfer_group=" + chargeExternalId));
        Assert.assertThat(payload, CoreMatchers.containsString("currency=GBP"));
        Assert.assertThat(payload, CoreMatchers.containsString("source=" + stripeSourceId));
        Assert.assertThat(payload, CoreMatchers.containsString("capture=false"));
        Assert.assertThat(payload, CoreMatchers.containsString("on_behalf_of=" + stripeConnectAccountId));
        Assert.assertThat(payload, CoreMatchers.containsString("description=" + description));
    }

    @Test
    public void shouldSetCorrectOrderRequestTypeForAuthorisationWithout3DS() {
        assertThat(stripeAuthoriseRequest.getGatewayOrder().getOrderRequestType(), is(OrderRequestType.AUTHORISE));

    }

    @Test
    public void shouldSetCorrectOrderRequestTypeForAuthorisationWith3DS() {
        Auth3dsResponseGatewayRequest authorisationGatewayRequest = new Auth3dsResponseGatewayRequest(charge, new Auth3dsDetails());

        assertThat(StripeAuthoriseRequest
                        .of(stripeSourceId, authorisationGatewayRequest, stripeGatewayConfig)
                        .getGatewayOrder().getOrderRequestType(),
                is(OrderRequestType.AUTHORISE_3DS));
    }

    @Test
    public void shouldSetCorrectIdempotencyKey() {
        assertThat(stripeAuthoriseRequest.getHeaders().get("Idempotency-Key"), is("authorise" + chargeExternalId));
    }
}
