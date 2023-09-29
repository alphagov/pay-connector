package uk.gov.pay.connector.gateway.stripe.request;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayPaymentInfo;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayAuthRequestFixture.anApplePayAuthRequest;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayPaymentInfoFixture.anApplePayPaymentInfo;

@ExtendWith(MockitoExtension.class)
class StripeTokenRequestTest {
    
    private final String stripeConnectAccountId = "stripeConnectAccountId";

    private final String paymentData = "some-payment-data";
    private final String network = "a-network";
    private final String displayName = "a-display-name";
    private final String transactionIdentifier = "a-transaction-identifier";

    private final String stripeBaseUrl = "stripeUrl";
    private final String frontendUrl = "frontendUrl";

    private GatewayAccountEntity gatewayAccount;
    
    @Mock
    private ChargeEntity charge;
    @Mock
    private StripeAuthTokens stripeAuthTokens;
    @Mock
    private StripeGatewayConfig stripeGatewayConfig;


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
        when(charge.getGatewayAccountCredentialsEntity()).thenReturn(gatewayAccountCredentialsEntity);
    }
    
    @Test
    void shouldCreateCorrectUrl() {
        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
        ApplePayAuthorisationGatewayRequest gatewayRequest = buildWalletGatewayAuthorisationRequest();
        StripeTokenRequest stripeTokenRequest = StripeTokenRequest.of(gatewayRequest, stripeGatewayConfig);

        assertThat(stripeTokenRequest.getUrl(), is(URI.create(stripeBaseUrl + "/v1/tokens")));
    }

    @Test
    void shouldNotIncludeIdempotencyHeader() {
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        ApplePayAuthorisationGatewayRequest gatewayRequest = buildWalletGatewayAuthorisationRequest();
        StripeTokenRequest stripeTokenRequest = StripeTokenRequest.of(gatewayRequest, stripeGatewayConfig);

        assertThat(stripeTokenRequest.getHeaders(), not(hasKey("Idempotency-Key")));
    }

    @Test
    void payloadShouldIncludeExpectedFields() {
        ApplePayAuthorisationGatewayRequest gatewayRequest = buildWalletGatewayAuthorisationRequest();
        StripeTokenRequest stripeTokenRequest = StripeTokenRequest.of(gatewayRequest, stripeGatewayConfig);
        
        String payload = stripeTokenRequest.getGatewayOrder().getPayload();
        assertThat(payload, containsString("pk_token=" + paymentData));
        assertThat(payload, containsString("pk_token_instrument_name=" + displayName));
        assertThat(payload, containsString("pk_token_payment_network=" + network));
        assertThat(payload, containsString("pk_token_transaction_id=" + transactionIdentifier));
    }

    @NotNull
    private ApplePayAuthorisationGatewayRequest buildWalletGatewayAuthorisationRequest() {
        ApplePayPaymentInfo applePayPaymentInfo = anApplePayPaymentInfo()
                .withNetwork(network)
                .withDisplayName(displayName)
                .withTransactionIdentifier(transactionIdentifier)
                .build();
        ApplePayAuthRequest applePayAuthRequest = anApplePayAuthRequest()
                .withApplePaymentData(paymentData)
                .withApplePaymentInfo(applePayPaymentInfo)
                .build();
        return new ApplePayAuthorisationGatewayRequest(charge, applePayAuthRequest);
    }
}
