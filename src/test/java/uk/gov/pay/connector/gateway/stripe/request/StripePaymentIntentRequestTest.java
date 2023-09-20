package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.applepay.ApplePayAuthRequestFixture;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;

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
import static uk.gov.pay.connector.model.domain.applepay.ApplePayAuthRequestFixture.anApplePayAuthRequest;

@ExtendWith(MockitoExtension.class)
class StripePaymentIntentRequestTest {

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
    private final String customerId = "a-customer-id";

    @BeforeEach
    void setUp() {
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
        when(charge.getAmount()).thenReturn(amount);
        when(charge.getDescription()).thenReturn(description);
        when(charge.getGatewayAccountCredentialsEntity()).thenReturn(gatewayAccountCredentialsEntity);
    }

    @Test
    void shouldHaveCorrectParametersWithAddress() {
        StripePaymentIntentRequest stripePaymentIntentRequest = createOneOffStripePaymentIntentRequest();

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
        assertThat(payload, containsString("return_url=" + URLEncoder.encode(frontendUrl + "/card_details/" + chargeExternalId + "/3ds_required_in", UTF_8)));
        assertThat(payload, containsString(URLEncoder.encode("metadata[govuk_pay_transaction_external_id]", UTF_8) + "=" + chargeExternalId));
        assertThat(payload, not(containsString("customer=")));
        assertThat(payload, not(containsString("setup_future_usage=")));
        assertThat(payload, not(containsString("off_session=")));
        assertThat(payload, not(containsString("payment_method_data[type]=")));
        assertThat(payload, not(containsString("payment_method_data[card][token]=")));
    }

    @Test
    void createsCorrectIdempotencyKey() {
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        StripePaymentIntentRequest stripePaymentIntentRequest = createOneOffStripePaymentIntentRequest();

        assertThat(
                stripePaymentIntentRequest.getHeaders().get("Idempotency-Key"),
                is("payment_intent" + chargeExternalId));
    }

    @Test
    void shouldCreateCorrectUrl() {
        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
        StripePaymentIntentRequest stripePaymentIntentRequest = createOneOffStripePaymentIntentRequest();

        assertThat(stripePaymentIntentRequest.getUrl(), is(URI.create(stripeBaseUrl + "/v1/payment_intents")));
    }


    @Test
    void shouldIncludeMotoFlagWhenChargeIsMoto() {
        when(charge.isMoto()).thenReturn(true);

        StripePaymentIntentRequest stripePaymentIntentRequest = createOneOffStripePaymentIntentRequest();

        String payload = stripePaymentIntentRequest.getGatewayOrder().getPayload();
        assertThat(payload, containsString(URLEncoder.encode("payment_method_options[card[moto]]", UTF_8) + "=true"));
    }
    
    @Test
    void shouldNotIncludeMotoFlagWhenChargeIsNotMoto() {
        when(charge.isMoto()).thenReturn(false);

        StripePaymentIntentRequest stripePaymentIntentRequest = createOneOffStripePaymentIntentRequest();

        String payload = stripePaymentIntentRequest.getGatewayOrder().getPayload();
        assertThat(payload, not(containsString(URLEncoder.encode("payment_method_options[card[moto]]", UTF_8))));
    }

    @Test
    void shouldIncludeCustomerIdAndSetupFutureUsageFlagForSetupFutureUsageRequest() {
        var authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, new AuthCardDetails());
        var stripePaymentIntentRequest = StripePaymentIntentRequest.createPaymentIntentRequestWithSetupFutureUsage(
                authorisationGatewayRequest, paymentMethodId, customerId, stripeGatewayConfig, frontendUrl);
        String payload = stripePaymentIntentRequest.getGatewayOrder().getPayload();

        assertThat(payload, containsString("customer=" + customerId));
        assertThat(payload, containsString("setup_future_usage=off_session"));
        assertThat(payload, not(containsString("off_session=")));
    }

    @Test
    void shouldIncludeOffSessionFlagForUseSavedPaymentDetails() {
        RecurringPaymentAuthorisationGatewayRequest authRequest = RecurringPaymentAuthorisationGatewayRequest.valueOf(charge);
        StripePaymentIntentRequest paymentIntentRequest = StripePaymentIntentRequest.createPaymentIntentRequestUseSavedPaymentDetails(
                authRequest, paymentMethodId, customerId, stripeGatewayConfig, frontendUrl);
        String payload = paymentIntentRequest.getGatewayOrder().getPayload();

        assertThat(payload, containsString("customer=" + customerId));
        assertThat(payload, containsString("off_session=true"));
        assertThat(payload, not(containsString("setup_future_usage=")));
    }

    @Test
    void shouldIncludeTokenIdForCreateFromToken() {
        String tokenId = "a-token-id";
        
        ApplePayAuthRequest applePayAuthRequest = anApplePayAuthRequest().build();
        var authRequest = WalletAuthorisationGatewayRequest.valueOf(charge, applePayAuthRequest);
        StripePaymentIntentRequest paymentIntentRequest = StripePaymentIntentRequest.createPaymentIntentRequestWithToken(
                authRequest, tokenId, stripeGatewayConfig, frontendUrl);
        String payload = paymentIntentRequest.getGatewayOrder().getPayload();

        assertThat(payload, containsString(URLEncoder.encode("payment_method_data[type]", UTF_8) + "=" + "card"));
        assertThat(payload, containsString(URLEncoder.encode("payment_method_data[card][token]", UTF_8) + "=" + tokenId));
        assertThat(payload, containsString("amount=" + amount));
        assertThat(payload, containsString("confirmation_method=automatic"));
        assertThat(payload, containsString("capture_method=manual"));
        assertThat(payload, containsString("currency=GBP"));
        assertThat(payload, containsString("transfer_group=" + chargeExternalId));
        assertThat(payload, containsString("on_behalf_of=" + stripeConnectAccountId));
        assertThat(payload, containsString("confirm=true"));
        assertThat(payload, containsString("description=" + description));
        assertThat(payload, containsString("return_url=" + URLEncoder.encode(frontendUrl + "/card_details/" + chargeExternalId + "/3ds_required_in", UTF_8)));
        assertThat(payload, containsString(URLEncoder.encode("metadata[govuk_pay_transaction_external_id]", UTF_8) + "=" + chargeExternalId));
        assertThat(payload, not(containsString("payment_method=")));
        assertThat(payload, not(containsString("customer=")));
        assertThat(payload, not(containsString("setup_future_usage=")));
        assertThat(payload, not(containsString("off_session=")));
        
        
    }

    private StripePaymentIntentRequest createOneOffStripePaymentIntentRequest() {
        var authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, new AuthCardDetails());
        return StripePaymentIntentRequest.createOneOffPaymentIntentRequest(authorisationGatewayRequest, paymentMethodId, stripeGatewayConfig, frontendUrl);
    }
}
