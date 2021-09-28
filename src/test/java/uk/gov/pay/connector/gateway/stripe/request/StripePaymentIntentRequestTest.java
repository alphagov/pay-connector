package uk.gov.pay.connector.gateway.stripe.request;

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
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@RunWith(MockitoJUnitRunner.class)
public class StripePaymentIntentRequestTest {

    @Mock
    private ChargeEntity charge;

    private GatewayAccountEntity gatewayAccount;

    @Mock
    private StripeAuthTokens stripeAuthTokens;

    @Mock
    private StripeGatewayConfig stripeGatewayConfig;

    private final String stripeConnectAccountId = "stripeConnectAccountId";
    private final String chargeExternalId = "payChargeExternalId";
    private final String stripeBaseUrl = "stripeUrl";
    private final String paymentMethodId = "123abc";
    private final String frontendUrl = "frontendUrl";
    private final Long amount = 100L;
    private final String description = "description";

    @Before
    public void setUp() {
        gatewayAccount = aGatewayAccountEntity()
                .withGatewayName("stripe")
                .build();
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", "stripeConnectAccountId"))
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccount.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        when(charge.getGatewayAccount()).thenReturn(gatewayAccount);
        when(charge.getExternalId()).thenReturn(chargeExternalId);
//        when(charge.getAmount()).thenReturn(amount);
//        when(charge.getDescription()).thenReturn(description);
        when(charge.getGatewayAccountCredentialsEntity()).thenReturn(gatewayAccountCredentialsEntity);
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
//        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
    }

//    @Test
//    public void shouldHaveCorrectParametersWithAddress() {
//        StripeConfirmPaymentIntentRequest stripeConfirmPaymentIntentRequest = createStripePaymentIntentRequest();
//
//        String payload = stripeConfirmPaymentIntentRequest.getGatewayOrder().getPayload();
//        assertThat(payload, containsString("payment_method=" + paymentMethodId));
//        assertThat(payload, containsString("amount=" + amount));
//        assertThat(payload, containsString("confirmation_method=automatic"));
//        assertThat(payload, containsString("capture_method=manual"));
//        assertThat(payload, containsString("currency=GBP"));
//        assertThat(payload, containsString("transfer_group=" + chargeExternalId));
//        assertThat(payload, containsString("on_behalf_of=" + stripeConnectAccountId));
//        assertThat(payload, containsString("confirm=true"));
//        assertThat(payload, containsString("description=" + description));
//        assertThat(payload, containsString("return_url=" + URLEncoder.encode(frontendUrl + "/card_details/" + chargeExternalId + "/3ds_required_in", UTF_8)));
//    }

    @Test
    public void createsCorrectIdempotencyKey() {
        StripeConfirmPaymentIntentRequest stripeConfirmPaymentIntentRequest = createStripePaymentIntentRequest();

        assertThat(
                stripeConfirmPaymentIntentRequest.getHeaders().get("Idempotency-Key"),
                is("payment_intent_confirmation" + chargeExternalId));
    }

//    @Test
//    public void shouldCreateCorrectUrl() {
//        StripeConfirmPaymentIntentRequest stripeConfirmPaymentIntentRequest = createStripePaymentIntentRequest();
//
//        assertThat(stripeConfirmPaymentIntentRequest.getUrl(), is(URI.create(stripeBaseUrl + "/v1/payment_intents/confirm")));
//    }


//    @Test
//    public void shouldIncludeMotoFlagWhenChargeIsMoto() {
//        when(charge.isMoto()).thenReturn(true);
//
//        StripeConfirmPaymentIntentRequest stripeConfirmPaymentIntentRequest = createStripePaymentIntentRequest();
//
//        String payload = stripeConfirmPaymentIntentRequest.getGatewayOrder().getPayload();
//        assertThat(payload, containsString(URLEncoder.encode("payment_method_options[card[moto]]", UTF_8) + "=true"));
//    }
//    
//    @Test
//    public void shouldNotIncludeMotoFlagWhenChargeIsNotMoto() {
//        when(charge.isMoto()).thenReturn(false);
//
//        StripeConfirmPaymentIntentRequest stripeConfirmPaymentIntentRequest = createStripePaymentIntentRequest();
//
//        String payload = stripeConfirmPaymentIntentRequest.getGatewayOrder().getPayload();
//        assertThat(payload, not(containsString(URLEncoder.encode("payment_method_options[card[moto]]", UTF_8))));
//    }
    
    private StripeConfirmPaymentIntentRequest createStripePaymentIntentRequest() {
        var authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, new AuthCardDetails());
        return StripeConfirmPaymentIntentRequest.of(authorisationGatewayRequest, paymentMethodId, stripeGatewayConfig, frontendUrl);
    }
}
