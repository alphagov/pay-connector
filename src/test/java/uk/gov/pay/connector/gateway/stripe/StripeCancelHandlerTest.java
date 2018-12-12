package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCancelHandler;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class StripeCancelHandlerTest {

    private StripeCancelHandler stripeCancelHandler;
    @Mock 
    private StripeGatewayClient client;
    @Mock 
    private StripeGatewayConfig stripeGatewayConfig;

    @Before
    public void setup() {
        stripeCancelHandler = new StripeCancelHandler(client, stripeGatewayConfig);
    }
    
    @Test
    public void shouldCancelPaymentSuccessfully() {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(charge);
        final GatewayResponse<BaseCancelResponse> response = stripeCancelHandler.cancel(request);
        assertThat(response.isSuccessful(), is(true));
    }
    
    @Test
    public void shouldHandle4xxFromStripeGateway() throws Exception {
        Response mockedResponse = mock(Response.class);
        StripeErrorResponse stripeErrorResponse = new ObjectMapper().readValue(load(STRIPE_ERROR_RESPONSE), StripeErrorResponse.class);
        GatewayClientException gatewayClientException = new GatewayClientException("Unexpected HTTP status code 402 from gateway", mockedResponse);
        when(mockedResponse.readEntity(StripeErrorResponse.class)).thenReturn(stripeErrorResponse);
        when(client.postRequest(any(URI.class), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(gatewayClientException);
        
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(charge);

        final GatewayResponse<BaseCancelResponse> gatewayResponse = stripeCancelHandler.cancel(request);
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), Is.is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), Is.is("No such charge: ch_123456 or something similar"));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }
    
    @Test
    public void shouldHandle5xxFromStripeGateway() throws Exception {
        DownstreamException downstreamException = new DownstreamException(SC_SERVICE_UNAVAILABLE, "Problem with Stripe servers");
        when(client.postRequest(any(URI.class), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(downstreamException);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(charge);

        final GatewayResponse<BaseCancelResponse> gatewayResponse = stripeCancelHandler.cancel(request);
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), Is.is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), containsString("An internal server error occurred while cancelling external charge id: " + request.getExternalChargeId()));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), Is.is(UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
    }
    
    @Test
    public void shouldHandleGatewayException() throws Exception {
        GatewayException gatewayException = new GatewayException("/v1/refunds", new SocketTimeoutException());
        when(client.postRequest(any(URI.class), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(gatewayException);
        
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(charge);

        final GatewayResponse<BaseCancelResponse> gatewayResponse = stripeCancelHandler.cancel(request);
        assertThat(gatewayResponse.isFailed(), is(true));
    }
}
