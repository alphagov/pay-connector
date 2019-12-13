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
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StripePaymentIntentRequestTest {
    private final String stripeConnectAccountId = "stripeConnectAccountId";
    private final String chargeExternalId = "payChargeExternalId";
    private final String stripeBaseUrl = "stripeUrl";

    @Mock
    ChargeEntity charge;
    @Mock
    GatewayAccountEntity gatewayAccount;
    @Mock
    StripeAuthTokens stripeAuthTokens;
    @Mock
    StripeGatewayConfig stripeGatewayConfig;
    private String paymentMethodId = "123abc";
    private String frontendUrl = "frontendUrl";
    private Long amount = 100L;
    private String description = "description";

    @Before
    public void setUp() {
        when(gatewayAccount.getCredentials()).thenReturn(ImmutableMap.of("stripe_account_id", stripeConnectAccountId));
        when(charge.getGatewayAccount()).thenReturn(gatewayAccount);
        when(charge.getExternalId()).thenReturn(chargeExternalId);
        when(charge.getAmount()).thenReturn(amount);
        when(charge.getDescription()).thenReturn(description);
        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
    }

    @Test
    public void shouldHaveCorrectParametersWithAddress() {
        CardAuthorisationGatewayRequest authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, new AuthCardDetails());
        StripePaymentIntentRequest stripePaymentIntentRequest = StripePaymentIntentRequest.of(authorisationGatewayRequest, paymentMethodId, stripeGatewayConfig, frontendUrl);
        
        String payload = stripePaymentIntentRequest.getGatewayOrder().getPayload();
        assertThat(payload, containsString("payment_method=" + paymentMethodId));
        assertThat(payload, containsString("amount=" + amount));
        assertThat(payload, containsString("confirmation_method=automatic"));
        assertThat(payload, containsString("capture_method=manual"));
        assertThat(payload, containsString("currency=GBP"));
        assertThat(payload, containsString("transfer_group=" + chargeExternalId));
        assertThat(payload, containsString("on_behalf_of=" + stripeConnectAccountId));
        assertThat(payload, containsString("confirm=true"));
        assertThat(payload, containsString("description=" + description));
        assertThat(payload, containsString("return_url=" + frontendUrl + "%2Fcard_details%2F" + chargeExternalId + "%2F3ds_required_in"));
    }

    @Test
    public void shouldSetFlagIfChargeIsConfiguredForMoto() {
        when(charge.isMoto()).thenReturn(true);
        CardAuthorisationGatewayRequest authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, new AuthCardDetails());
        StripePaymentIntentRequest stripePaymentIntentRequest = StripePaymentIntentRequest.of(authorisationGatewayRequest, paymentMethodId, stripeGatewayConfig, frontendUrl);

        
        String payload = stripePaymentIntentRequest.getGatewayOrder().getPayload();
        assertThat(payload, containsString("payment_method_options%5Bcard%5BisMoto%5D%5D=true"));
    }

    @Test
    public void shouldIgnoreFlagIfChargeIsNotConfiguredForMoto() {
        when(charge.isMoto()).thenReturn(false);
        CardAuthorisationGatewayRequest authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, new AuthCardDetails());
        StripePaymentIntentRequest stripePaymentIntentRequest = StripePaymentIntentRequest.of(authorisationGatewayRequest, paymentMethodId, stripeGatewayConfig, frontendUrl);


        String payload = stripePaymentIntentRequest.getGatewayOrder().getPayload();
        assertThat(payload, not(containsString("isMoto")));
    }

    @Test
    public void createsCorrectIdempotencyKey() {
        CardAuthorisationGatewayRequest authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, new AuthCardDetails());
        StripePaymentIntentRequest stripePaymentIntentRequest = StripePaymentIntentRequest.of(authorisationGatewayRequest, paymentMethodId, stripeGatewayConfig, frontendUrl);

        assertThat(
                stripePaymentIntentRequest.getHeaders().get("Idempotency-Key"),
                is("payment_intent" + chargeExternalId));
    }

    @Test
    public void shouldCreateCorrectUrl() {
        CardAuthorisationGatewayRequest authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, new AuthCardDetails());
        StripePaymentIntentRequest stripePaymentIntentRequest = StripePaymentIntentRequest.of(authorisationGatewayRequest, paymentMethodId, stripeGatewayConfig, frontendUrl);
        assertThat(stripePaymentIntentRequest.getUrl(), is(URI.create(stripeBaseUrl + "/v1/payment_intents")));
    }
}
