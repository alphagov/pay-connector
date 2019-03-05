package uk.gov.pay.connector.gateway.stripe;

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
import uk.gov.pay.connector.gateway.GatewayErrorException.GatewayConnectionErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCancelHandler;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import javax.ws.rs.core.MediaType;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class StripeCancelHandlerTest {

    private StripeCancelHandler stripeCancelHandler;

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
    public void shouldCancelPaymentSuccessfully() {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(charge);
        final GatewayResponse<BaseCancelResponse> response = stripeCancelHandler.cancel(request);
        assertThat(response.isSuccessful(), is(true));
    }

    @Test
    public void shouldHandleGatewayErrorExceptionFromStripeGateway() throws Exception {

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(charge);

        when(client.postRequestFor(
                any(URI.class), 
                request.getGatewayAccount(),
                any(GatewayOrder.class), 
                anyMap())).thenThrow(new GatewayConnectionErrorException("U Must Paye", 402, load(STRIPE_ERROR_RESPONSE)));

        final GatewayResponse<BaseCancelResponse> gatewayResponse = stripeCancelHandler.cancel(request);
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), Is.is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), Is.is("No such charge: ch_123456 or something similar"));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }
}
