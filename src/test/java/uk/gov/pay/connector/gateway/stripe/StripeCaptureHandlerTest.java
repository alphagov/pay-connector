package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayErrorException.GatewayConnectionErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCaptureHandler;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;

import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class StripeCaptureHandlerTest {

    private StripeCaptureHandler stripeCaptureHandler;
    private URI captureUri;

    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    @Mock
    private GatewayClient gatewayClient;
    @Mock
    private StripeGatewayClientResponse response;

    private CaptureGatewayRequest captureGatewayRequest;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());

    @Before
    public void setup() {
        when(stripeGatewayConfig.getUrl()).thenReturn("http://stripe.url");
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(mock(StripeAuthTokens.class));
        stripeCaptureHandler = new StripeCaptureHandler(gatewayClient, stripeGatewayConfig, objectMapper);

        GatewayAccountEntity gatewayAccount = buildGatewayAccountEntity();

        final String transactionId = "ch_1231231123123";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId(transactionId)
                .build();

        captureGatewayRequest = CaptureGatewayRequest.valueOf(chargeEntity);
        captureUri = URI.create(stripeGatewayConfig.getUrl() + "/v1/charges/" + transactionId + "/capture");
    }

    @Test
    public void shouldCapture() throws Exception {
        GatewayClient.Response captureResponse = mock(GatewayClient.Response.class);
        when(captureResponse.getStatus()).thenReturn(SC_OK);
        when(captureResponse.getEntity()).thenReturn(load(STRIPE_CAPTURE_SUCCESS_RESPONSE));
        
        when(gatewayClient.postRequestFor(
                eq(captureUri), 
                eq(captureGatewayRequest.getGatewayAccount()), 
                any(GatewayOrder.class), 
                anyMap())).thenReturn(captureResponse);

        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertTrue(response.isSuccessful());
        assertThat(response.state(), is(CaptureResponse.ChargeState.COMPLETE));
        assertThat(response.getTransactionId().isPresent(), is(true));
        assertThat(response.getTransactionId().get(), is("ch_123456"));
    }

    @Test
    public void shouldNotCaptureIfGatewayClientThrowsGatewayErrorException() throws Exception {
        when(gatewayClient.postRequestFor(
                eq(captureUri),
                eq(captureGatewayRequest.getGatewayAccount()),
                any(GatewayOrder.class),
                anyMap())).thenThrow(new GatewayConnectionErrorException("Unexpected HTTP status code 402 from gateway", 402, load(STRIPE_ERROR_RESPONSE)));
        
        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.state(), is(nullValue()));
        assertThat(response.toString(), containsString("error: No such charge: ch_123456 or something similar"));
        assertThat(response.toString(), containsString("error code: resource_missing"));
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
