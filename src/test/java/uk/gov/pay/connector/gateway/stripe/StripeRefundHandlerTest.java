package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeRefundHandler;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_REFUND_ERROR_ALREADY_REFUNDED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_REFUND_ERROR_GREATER_AMOUNT_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_REFUND_FULL_CHARGE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class StripeRefundHandlerTest {
    private StripeRefundHandler refundHandler;
    private RefundGatewayRequest refundRequest;
    private URI refundsUri;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());

    @Mock
    private StripeGatewayClient gatewayClient;
    @Mock
    private StripeGatewayConfig gatewayConfig;
    @Mock
    private StripeGatewayClientResponse response;

    @Before
    public void setup() {
        refundHandler = new StripeRefundHandler(gatewayClient, gatewayConfig, objectMapper);
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().withAmount(100L).build();
        refundRequest = RefundGatewayRequest.valueOf(refundEntity);
        when(gatewayConfig.getAuthTokens()).thenReturn(mock(StripeAuthTokens.class));
        refundsUri = URI.create(gatewayConfig.getUrl() + "/v1/refunds");
    }

    @Test
    public void shouldRefundInFull() throws GatewayClientException, GatewayException, DownstreamException {
        final String jsonResponse = load(STRIPE_REFUND_FULL_CHARGE_RESPONSE);
        when(gatewayClient.postRequest(eq(refundsUri), anyString(), any(Map.class), any(MediaType.class), anyString())).thenReturn(jsonResponse);

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);
        assertNotNull(refund);
        assertTrue(refund.isSuccessful());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.COMPLETE));
        assertThat(refund.getReference().get(), is("re_1DRiccHj08j21DRiccHj08j2_test"));
    }

    @Test
    public void shouldNotRefund_whenAmountIsMoreThanChargeAmount() throws GatewayClientException, GatewayException, DownstreamException {
        final String jsonResponse = load(STRIPE_REFUND_ERROR_GREATER_AMOUNT_RESPONSE);
        when(response.getPayload()).thenReturn(jsonResponse);

        GatewayClientException gatewayClientException = new GatewayClientException("Unexpected HTTP status code 402 from gateway", response);
        when(gatewayClient.postRequest(eq(refundsUri), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(gatewayClientException);

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);
        assertNotNull(refund);
        assertTrue(refund.getError().isPresent());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.ERROR));
        assertThat(refund.getError().get().getMessage(), is("Stripe refund response (error: Refund amount (£5.01) is greater than charge amount (£5.00))"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_anAlreadyRefundedCharge() throws GatewayClientException, GatewayException, DownstreamException {
        String jsonResponse = load(STRIPE_REFUND_ERROR_ALREADY_REFUNDED_RESPONSE);
        when(response.getPayload()).thenReturn(jsonResponse);

        GatewayClientException gatewayClientException = new GatewayClientException("Unexpected HTTP status code 402 from gateway", response);
        when(gatewayClient.postRequest(eq(refundsUri), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(gatewayClientException);

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);

        assertNotNull(refund);
        assertTrue(refund.getError().isPresent());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.ERROR));
        assertThat(refund.getError().get().getMessage(), is("Stripe refund response (error: The transfer tr_blah_blah_blah is already fully reversed.)"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_whenStatusCode4xx() throws Exception {
        final String jsonResponse = load(STRIPE_ERROR_RESPONSE);
        when(response.getPayload()).thenReturn(jsonResponse);

        GatewayClientException gatewayClientException = new GatewayClientException("Unexpected HTTP status code 402 from gateway", response);
        when(gatewayClient.postRequest(eq(refundsUri), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(gatewayClientException);

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);
        assertNotNull(refund);
        assertTrue(refund.getError().isPresent());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.ERROR));
        assertThat(refund.getError().get().getMessage(), Is.is("Stripe refund response (error code: resource_missing, error: No such charge: ch_123456 or something similar)"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_whenStatusCode5xx() throws Exception {
        DownstreamException downstreamException = new DownstreamException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Problem with Stripe servers");
        when(gatewayClient.postRequest(eq(refundsUri), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(downstreamException);

        GatewayRefundResponse response = refundHandler.refund(refundRequest);
        assertThat(response.getError().isPresent(), Is.is(true));
        assertThat(response.getError().get().getMessage(), containsString("An internal server error occurred while refunding Transaction id:"));
        assertThat(response.getError().get().getErrorType(), Is.is(GATEWAY_CONNECTION_ERROR));
    }
}
