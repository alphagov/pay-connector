package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeRefundHandler;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_REFUND_ERROR_ALREADY_REFUNDED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_REFUND_ERROR_GREATER_AMOUNT_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_REFUND_FULL_CHARGE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class StripeRefundHandlerTest {
    private StripeRefundHandler refundHandler;
    private RefundGatewayRequest refundRequest;

    @Mock
    private StripeGatewayClient gatewayClient;
    @Mock
    private StripeGatewayConfig gatewayConfig;
    @Mock
    private Response response;

    @Before
    public void setup() {
        refundHandler = new StripeRefundHandler(gatewayClient, gatewayConfig);
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().withAmount(100L).build();
        refundRequest = RefundGatewayRequest.valueOf(refundEntity);
    }

    @Test
    public void shouldRefundInFull() throws GatewayClientException, GatewayException, DownstreamException, IOException {
        Map<String, Object> responsePayloadMap = new ObjectMapper().readValue(load(STRIPE_REFUND_FULL_CHARGE_RESPONSE), HashMap.class);
        when(response.readEntity(Map.class)).thenReturn(responsePayloadMap);

        when(gatewayClient.postRequest(any(URI.class), anyString(), any(Map.class), any(MediaType.class), anyString())).thenReturn(response);

        final GatewayResponse<BaseRefundResponse> refund = refundHandler.refund(refundRequest);
        assertNotNull(refund);
        assertTrue(refund.isSuccessful());
        assertThat(refund.getBaseResponse().get().getReference().get(), is("re_1DRiccHj08j21DRiccHj08j2_test"));
    }

    @Test
    public void shouldNotRefund_whenAmountIsMoreThanChargeAmount() throws GatewayClientException, GatewayException, DownstreamException, IOException {
        StripeErrorResponse stripeErrorResponse = new ObjectMapper().readValue(load(STRIPE_REFUND_ERROR_GREATER_AMOUNT_RESPONSE), StripeErrorResponse.class);
        when(response.readEntity(StripeErrorResponse.class)).thenReturn(stripeErrorResponse);

        GatewayClientException gatewayClientException = new GatewayClientException("Unexpected HTTP status code 402 from gateway", response);
        when(gatewayClient.postRequest(any(URI.class), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(gatewayClientException);

        final GatewayResponse<BaseRefundResponse> refund = refundHandler.refund(refundRequest);
        assertNotNull(refund);
        assertTrue(refund.isFailed());
        assertTrue(refund.getGatewayError().isPresent());
        assertThat(refund.getGatewayError().get().getMessage(), is("Refund amount (£5.01) is greater than charge amount (£5.00)"));
        assertThat(refund.getGatewayError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_anAlreadyRefundedCharge() throws GatewayClientException, GatewayException, DownstreamException, IOException {
        StripeErrorResponse stripeErrorResponse = new ObjectMapper().readValue(load(STRIPE_REFUND_ERROR_ALREADY_REFUNDED_RESPONSE), StripeErrorResponse.class);
        when(response.readEntity(StripeErrorResponse.class)).thenReturn(stripeErrorResponse);

        GatewayClientException gatewayClientException = new GatewayClientException("Unexpected HTTP status code 402 from gateway", response);
        when(gatewayClient.postRequest(any(URI.class), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(gatewayClientException);

        final GatewayResponse<BaseRefundResponse> refund = refundHandler.refund(refundRequest);
        assertNotNull(refund);
        assertTrue(refund.isFailed());
        assertTrue(refund.getGatewayError().isPresent());
        assertThat(refund.getGatewayError().get().getMessage(), is("The transfer tr_blah_blah_blah is already fully reversed."));
        assertThat(refund.getGatewayError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_whenStatusCode4xx() throws Exception {
        StripeErrorResponse stripeErrorResponse = new ObjectMapper().readValue(load(STRIPE_ERROR_RESPONSE), StripeErrorResponse.class);
        when(response.readEntity(StripeErrorResponse.class)).thenReturn(stripeErrorResponse);

        GatewayClientException gatewayClientException = new GatewayClientException("Unexpected HTTP status code 402 from gateway", response);
        when(gatewayClient.postRequest(any(URI.class), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(gatewayClientException);

        final GatewayResponse<BaseRefundResponse> refund = refundHandler.refund(refundRequest);
        assertNotNull(refund);
        assertTrue(refund.isFailed());
        assertTrue(refund.getGatewayError().isPresent());
        assertThat(refund.getGatewayError().get().getMessage(), Is.is("No such charge: ch_123456 or something similar"));
        assertThat(refund.getGatewayError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_whenStatusCode5xx() throws Exception {
        DownstreamException downstreamException = new DownstreamException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Problem with Stripe servers");
        when(gatewayClient.postRequest(any(URI.class), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(downstreamException);

        GatewayResponse<BaseRefundResponse> response = refundHandler.refund(refundRequest);
        assertThat(response.isFailed(), Is.is(true));
        assertThat(response.getGatewayError().isPresent(), Is.is(true));
        assertThat(response.getGatewayError().get().getMessage(), containsString("An internal server error occurred. ErrorId:"));
        assertThat(response.getGatewayError().get().getErrorType(), Is.is(UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
    }
}
