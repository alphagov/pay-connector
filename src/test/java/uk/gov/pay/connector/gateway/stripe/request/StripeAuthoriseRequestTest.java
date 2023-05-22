package uk.gov.pay.connector.gateway.stripe.request;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class StripeAuthoriseRequestTest {
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

    GatewayAccountEntity gatewayAccount;
    @Mock
    StripeAuthTokens stripeAuthTokens;
    @Mock
    StripeGatewayConfig stripeGatewayConfig;

    @BeforeEach
    void setUp() {
        gatewayAccount = aGatewayAccountEntity()
                .withGatewayName("stripe")
                .build();
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", stripeConnectAccountId))
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccount.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        when(charge.getGatewayAccount()).thenReturn(gatewayAccount);
        when(charge.getExternalId()).thenReturn(chargeExternalId);
        when(charge.getDescription()).thenReturn(description);
        when(charge.getReference()).thenReturn(ServicePaymentReference.of("a-reference"));
        when(charge.getAmount()).thenReturn(amount);
        when(charge.getGatewayAccountCredentialsEntity()).thenReturn(gatewayAccountCredentialsEntity);

        authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, new AuthCardDetails());

        stripeAuthoriseRequest = StripeAuthoriseRequest.of(stripeSourceId, authorisationGatewayRequest, stripeGatewayConfig);
    }

    @Test
    void shouldCreateAuthoriseCaptureUrl() {
        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
        assertThat(
                stripeAuthoriseRequest.getUrl(),
                is(URI.create(stripeBaseUrl + "/v1/charges"))
        );
    }

    @Test
    void shouldCreateCorrectPayload() {
        String payload = stripeAuthoriseRequest.getGatewayOrder().getPayload();

        assertThat(payload, CoreMatchers.containsString("amount=" + amount));
        assertThat(payload, CoreMatchers.containsString("transfer_group=" + chargeExternalId));
        assertThat(payload, CoreMatchers.containsString("currency=GBP"));
        assertThat(payload, CoreMatchers.containsString("source=" + stripeSourceId));
        assertThat(payload, CoreMatchers.containsString("capture=false"));
        assertThat(payload, CoreMatchers.containsString("on_behalf_of=" + stripeConnectAccountId));
        assertThat(payload, CoreMatchers.containsString("description=" + description));
    }

    @Test
    void shouldSetCorrectOrderRequestTypeForAuthorisationWithout3DS() {
        assertThat(stripeAuthoriseRequest.getGatewayOrder().getOrderRequestType(), is(OrderRequestType.AUTHORISE));

    }

    @Test
    void shouldSetCorrectOrderRequestTypeForAuthorisationWith3DS() {
        Auth3dsResponseGatewayRequest authorisationGatewayRequest = new Auth3dsResponseGatewayRequest(charge, new Auth3dsResult());

        assertThat(StripeAuthoriseRequest
                        .of(stripeSourceId, authorisationGatewayRequest, stripeGatewayConfig)
                        .getGatewayOrder().getOrderRequestType(),
                is(OrderRequestType.AUTHORISE_3DS));
    }

    @Test
    void shouldSetCorrectIdempotencyKey() {
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        assertThat(stripeAuthoriseRequest.getHeaders().get("Idempotency-Key"), is("authorise" + chargeExternalId));
    }
}
