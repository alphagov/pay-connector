package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import jakarta.ws.rs.core.MediaType;
import java.net.URI;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeSubmitTestDisputeEvidenceRequestTest {

    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    @Mock
    private StripeAuthTokens stripeAuthTokens;

    private String disputeId = "a-dispute-id";
    private String evidenceText = "winning_evidence";
    private String transactionExternalId = "transaction-external-id";
    private StripeSubmitTestDisputeEvidenceRequest request;

    @BeforeEach
    public void setUp() {
        request = StripeSubmitTestDisputeEvidenceRequest.of(stripeGatewayConfig, disputeId, evidenceText, transactionExternalId);
    }

    @Test
    void shouldReturnCorrectHeader() {
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        when(stripeAuthTokens.getTest()).thenReturn("test-token");

        Map<String, String> headers = request.getHeaders();

        assertThat(headers.get("Idempotency-Key"), is("dispute.updatetransaction-external-id"));
        assertThat(headers.get("Authorization"), is("Bearer test-token"));
        assertThat(headers.get("Stripe-Version"), is("2019-05-16"));
    }

    @Test
    void shouldCreateGatewayOrder() {
        GatewayOrder gatewayOrder = request.getGatewayOrder();
        assertThat(gatewayOrder.getOrderRequestType(), is(OrderRequestType.STRIPE_UPDATE_DISPUTE));
        assertThat(gatewayOrder.getMediaType().toString(), is(MediaType.APPLICATION_FORM_URLENCODED));
        assertThat(gatewayOrder.getPayload(), is("evidence%5Buncategorized_text%5D=winning_evidence"));
    }

    @Test
    void shouldReturnTestAccountType() {
        assertThat(request.getGatewayAccountType(), is("test"));
    }

    @Test
    void shouldReturnStripeAsPaymentProvider() {
        assertThat(request.getPaymentProvider().toString(), is("STRIPE"));
    }

    @Test
    void shouldCreateCorrectUrl() {
        when(stripeGatewayConfig.getUrl()).thenReturn("https://example.org");
        URI uri = request.getUrl();
        assertThat(uri.getPath(), is("/v1/disputes/" + disputeId));
        assertThat(uri.toString(), is("https://example.org/v1/disputes/" + disputeId));
    }
}