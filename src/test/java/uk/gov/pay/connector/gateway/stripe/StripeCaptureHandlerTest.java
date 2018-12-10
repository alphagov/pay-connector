package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCaptureHandler;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class StripeCaptureHandlerTest {

    private StripeCaptureHandler stripeCaptureHandler;

    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    @Mock
    private StripeGatewayClient stripeGatewayClient;
    @Mock
    private Response response;
    
    private CaptureGatewayRequest captureGatewayRequest;
    
    @Before
    public void setup() {
        when(stripeGatewayConfig.getUrl()).thenReturn("http://stripe.url");
        stripeCaptureHandler = new StripeCaptureHandler(stripeGatewayClient, stripeGatewayConfig);

        GatewayAccountEntity gatewayAccount = buildGatewayAccountEntity();
        
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId("ch_1231231123123")
                .build();
        
        captureGatewayRequest = CaptureGatewayRequest.valueOf(chargeEntity);
    }

    @Test
    public void shouldCapture() throws Exception {
        Map<String, Object> responsePayloadMap = new ObjectMapper().readValue(load(STRIPE_CAPTURE_SUCCESS_RESPONSE), HashMap.class);
        when(response.readEntity(Map.class)).thenReturn(responsePayloadMap);

        when(stripeGatewayClient.postRequest(any(URI.class), anyString(), any(Map.class), any(MediaType.class), anyString())).thenReturn(response);

        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertTrue(response.isSuccessful());
        assertThat(response.state(), is(CaptureResponse.ChargeState.COMPLETE));
        assertThat(response.getTransactionId().isPresent(), is(true));
        assertThat(response.getTransactionId().get(), is("ch_123456"));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturns4xxHttpStatusCode() throws Exception {
        StripeErrorResponse stripeErrorResponse = new ObjectMapper().readValue(load(STRIPE_ERROR_RESPONSE), StripeErrorResponse.class);
        when(response.readEntity(StripeErrorResponse.class)).thenReturn(stripeErrorResponse);

        GatewayClientException gatewayClientException = new GatewayClientException("Unexpected HTTP status code 402 from gateway", response);
        when(stripeGatewayClient.postRequest(any(URI.class), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(gatewayClientException);
        
        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.toString(), containsString("error: No such charge: ch_123456 or something similar"));
        assertThat(response.toString(), containsString("error code: resource_missing"));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturns5xxHttpStatusCode() throws Exception {
        DownstreamException downstreamException = new DownstreamException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Problem with Stripe servers");
        when(stripeGatewayClient.postRequest(any(URI.class), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(downstreamException);

        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.getError().get().getMessage(), containsString("An internal server error occurred. ErrorId:"));
        assertThat(response.getError().get().getErrorType(), is(UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
    }

    private GatewayAccountEntity buildGatewayAccountEntity() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("stripe");
        gatewayAccount.setRequires3ds(false);
        gatewayAccount.setCredentials(ImmutableMap.of("stripe_account_id", "stripe_account_id"));
        gatewayAccount.setType(TEST);
        return gatewayAccount;
    }
}
