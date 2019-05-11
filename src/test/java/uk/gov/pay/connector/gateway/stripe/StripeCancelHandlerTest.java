package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCancelHandler;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class StripeCancelHandlerTest {

    private StripeCancelHandler stripeCancelHandler;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());

    @Mock
    private GatewayClient client;
    @Mock
    private StripeGatewayConfig stripeGatewayConfig;

    @Before
    public void setup() {
        stripeCancelHandler = new StripeCancelHandler(client, stripeGatewayConfig);
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(mock(StripeAuthTokens.class));
    }

    @Test
    public void shouldCancelPaymentSuccessfully() throws Exception {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(charge);
        final GatewayResponse<BaseCancelResponse> response = stripeCancelHandler.cancel(request);
        assertThat(response.isSuccessful(), is(true));
    }

    @Test
    public void shouldHandle4xxFromStripeGateway() throws Exception {
        GatewayErrorException exception = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", load(STRIPE_ERROR_RESPONSE), SC_BAD_REQUEST);
        when(client.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenThrow(exception);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(charge);

        final GatewayResponse<BaseCancelResponse> gatewayResponse = stripeCancelHandler.cancel(request);
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), Is.is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), Is.is("Unexpected HTTP status code 402 from gateway"));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), Is.is(GATEWAY_ERROR));
    }

    @Test
    public void shouldHandle5xxFromStripeGateway() throws Exception {
        GatewayErrorException exception = new GatewayErrorException("Problem with Stripe servers", "stripe server error", SC_SERVICE_UNAVAILABLE);
        when(client.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenThrow(exception);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(charge);

        final GatewayResponse<BaseCancelResponse> gatewayResponse = stripeCancelHandler.cancel(request);
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), Is.is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), Is.is("Problem with Stripe servers"));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), Is.is(GATEWAY_ERROR));
    }

    @Test
    public void shouldHandleGatewayException() throws Exception {
        GatewayConnectionTimeoutException gatewayException = new GatewayConnectionTimeoutException("couldn't connect to https://stripe.url");
        when(client.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenThrow(gatewayException);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(charge);

        final GatewayResponse<BaseCancelResponse> gatewayResponse = stripeCancelHandler.cancel(request);
        assertThat(gatewayResponse.isFailed(), is(true));
    }
}
