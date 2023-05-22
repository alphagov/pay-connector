package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class StripeCaptureRequestTest {
    private final String chargeExternalId = "payChargeExternalId";
    private final String stripeChargeId = "stripeChargeId";
    private final String stripeBaseUrl = "stripeUrl";
    private final String stripeLiveApiToken = "stripe_live_api_token";
    private final long gatewayAccountId = 123L;

    private CaptureGatewayRequest captureGatewayRequest;
    private StripeCaptureRequest stripeCaptureRequest;

    @Mock
    ChargeEntity charge;

    GatewayAccountEntity gatewayAccount;
    @Mock
    StripeAuthTokens stripeAuthTokens;
    @Mock
    StripeGatewayConfig stripeGatewayConfig;

    @BeforeEach
    void setUp() {
        gatewayAccount = aGatewayAccountEntity()
                .withId(gatewayAccountId)
                .withGatewayName("stripe")
                .withType(LIVE)
                .build();
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccount.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        when(charge.getGatewayTransactionId()).thenReturn(stripeChargeId);
        when(charge.getGatewayAccount()).thenReturn(gatewayAccount);
        when(charge.getExternalId()).thenReturn(chargeExternalId);
        when(charge.getGatewayAccountCredentialsEntity()).thenReturn(gatewayAccountCredentialsEntity);

        captureGatewayRequest = CaptureGatewayRequest.valueOf(charge);


        stripeCaptureRequest = StripePaymentIntentCaptureRequest.of(captureGatewayRequest, stripeGatewayConfig);
    }

    @Test
    void shouldCreateCorrectCaptureUrl() {
        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);

        assertThat(
                stripeCaptureRequest.getUrl(),
                is(URI.create(stripeBaseUrl + "/v1/payment_intents/" + stripeChargeId + "/capture"))
        );
    }

    @Test
    void shouldCreateCorrectCapturePayload() {
        assertThat(
                stripeCaptureRequest.getGatewayOrder().getPayload(),
                containsString("expand%5B%5D=charges.data.balance_transaction")
        );
    }

    @Test
    void shouldSetGatewayOrderToBeOfTypeCapture() {
        assertThat(
                stripeCaptureRequest.getGatewayOrder().getOrderRequestType(),
                is(OrderRequestType.CAPTURE)
        );
    }

    @Test
    void shouldCreateCorrectCaptureHeaders() {
        when(stripeAuthTokens.getLive()).thenReturn(stripeLiveApiToken);
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);

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
    void shouldHaveCorrectGatewayAccountType() {
        assertThat(
                stripeCaptureRequest.getGatewayAccountType(),
                is(gatewayAccount.getType())
        );
    }
}
