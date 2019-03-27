package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCaptureHandler;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_TRANSFER_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class StripeCaptureHandlerTest {

    private StripeCaptureHandler stripeCaptureHandler;
    private URI captureUri;
    private URI transferUri;

    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    @Mock
    private GatewayClient gatewayClient;

    private CaptureGatewayRequest captureGatewayRequest;
    private GatewayAccountEntity gatewayAccountEntity;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());

    @Before
    public void setup() {
        when(stripeGatewayConfig.getUrl()).thenReturn("http://stripe.url");
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(mock(StripeAuthTokens.class));
        stripeCaptureHandler = new StripeCaptureHandler(gatewayClient, stripeGatewayConfig, objectMapper);
        when(stripeGatewayConfig.getFeePercentage()).thenReturn(0.08);
        when(stripeGatewayConfig.isCollectFee()).thenReturn(true);

        gatewayAccountEntity = buildGatewayAccountEntity();

        final String transactionId = "ch_1231231123123";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withTransactionId(transactionId)
                .withAmount(10000L)
                .build();

        captureGatewayRequest = CaptureGatewayRequest.valueOf(chargeEntity);
        captureUri = URI.create(stripeGatewayConfig.getUrl() + "/v1/charges/" + transactionId + "/capture");
        transferUri = URI.create(stripeGatewayConfig.getUrl() + "/v1/transfers");
    }

    @Test
    public void shouldCapture() throws Exception {
        GatewayClient.Response gatewayCaptureResponse = mock(GatewayClient.Response.class);
        when(gatewayCaptureResponse.getEntity()).thenReturn(load(STRIPE_CAPTURE_SUCCESS_RESPONSE));
        GatewayClient.Response gatewayTransferResponse = mock(GatewayClient.Response.class);
        when(gatewayTransferResponse.getEntity()).thenReturn(load(STRIPE_TRANSFER_RESPONSE));

        when(gatewayClient.postRequestFor(eq(captureUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(gatewayCaptureResponse);
        when(gatewayClient.postRequestFor(eq(transferUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(gatewayTransferResponse);

        CaptureResponse captureResponse = stripeCaptureHandler.capture(captureGatewayRequest);
        assertTrue(captureResponse.isSuccessful());
        assertThat(captureResponse.state(), is(CaptureResponse.ChargeState.COMPLETE));
        assertThat(captureResponse.getTransactionId().isPresent(), is(true));
        assertThat(captureResponse.getTransactionId().get(), is("ch_123456"));
        assertThat(captureResponse.getFee().isPresent(), is(true));
        assertThat(captureResponse.getFee().get(), is(58L));
    }
    
    public void shouldCaptureWithFeeAndTransferCorrectAmountToConnectAccount() throws Exception {
        GatewayClient.Response gatewayCaptureResponse = mock(GatewayClient.Response.class);
        when(gatewayCaptureResponse.getEntity()).thenReturn(load(STRIPE_CAPTURE_SUCCESS_RESPONSE));
        GatewayClient.Response gatewayTransferResponse = mock(GatewayClient.Response.class);
        when(gatewayTransferResponse.getEntity()).thenReturn(load(STRIPE_TRANSFER_RESPONSE));

        when(gatewayClient.postRequestFor(eq(captureUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(gatewayCaptureResponse);
        when(gatewayClient.postRequestFor(eq(transferUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(gatewayTransferResponse);
        
        CaptureResponse captureResponse = stripeCaptureHandler.capture(captureGatewayRequest);

        ArgumentCaptor<GatewayOrder> netTransferPayloadCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(gatewayClient).postRequestFor(eq(captureUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class));
        verify(gatewayClient).postRequestFor(eq(transferUri), any(GatewayAccountEntity.class), netTransferPayloadCaptor.capture(), any(Map.class));

        List<NameValuePair> netTransferParams = URLEncodedUtils.parse(netTransferPayloadCaptor.getValue().getPayload(), UTF_8);
        
        assertThat(netTransferParams, hasItem(new BasicNameValuePair("amount", "9942")));
        
        assertTrue(captureResponse.isSuccessful());
        assertThat(captureResponse.state(), is(CaptureResponse.ChargeState.COMPLETE));
        assertThat(captureResponse.getTransactionId().isPresent(), is(true));
        assertThat(captureResponse.getTransactionId().get(), is("ch_123456"));
        assertThat(captureResponse.getFee().isPresent(), is(true));
        assertThat(captureResponse.getFee().get(), is(58L));
    }

    @Test
    public void shouldCaptureWithFee_feeCalculationShouldAlwaysRoundUp() throws Exception {
        final String transactionId = "ch_1231231123123";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withTransactionId(transactionId)
                .withAmount(10001L)
                .build();

        captureGatewayRequest = CaptureGatewayRequest.valueOf(chargeEntity);
        GatewayClient.Response gatewayCaptureResponse = mock(GatewayClient.Response.class);
        when(gatewayCaptureResponse.getEntity()).thenReturn(load(STRIPE_CAPTURE_SUCCESS_RESPONSE));
        GatewayClient.Response gatewayTransferResponse = mock(GatewayClient.Response.class);
        when(gatewayTransferResponse.getEntity()).thenReturn(load(STRIPE_TRANSFER_RESPONSE));

        when(gatewayClient.postRequestFor(eq(captureUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(gatewayCaptureResponse);
        when(gatewayClient.postRequestFor(eq(transferUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(gatewayTransferResponse);

        CaptureResponse captureResponse = stripeCaptureHandler.capture(captureGatewayRequest);
        
        assertTrue(captureResponse.isSuccessful());
        assertThat(captureResponse.getFee().get(), is(59L));
    }

    @Test
    public void shouldCaptureWithFee_feeCalculationShouldRoundUpTo1() throws Exception {
        final String transactionId = "ch_1231231123123";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withTransactionId(transactionId)
                .withAmount(1L)
                .build();

        captureGatewayRequest = CaptureGatewayRequest.valueOf(chargeEntity);
        GatewayClient.Response gatewayCaptureResponse = mock(GatewayClient.Response.class);
        when(gatewayCaptureResponse.getEntity()).thenReturn(load(STRIPE_CAPTURE_SUCCESS_RESPONSE));
        GatewayClient.Response gatewayTransferResponse = mock(GatewayClient.Response.class);
        when(gatewayTransferResponse.getEntity()).thenReturn(load(STRIPE_TRANSFER_RESPONSE));

        when(gatewayClient.postRequestFor(eq(captureUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(gatewayCaptureResponse);
        when(gatewayClient.postRequestFor(eq(transferUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(gatewayTransferResponse);

        CaptureResponse captureResponse = stripeCaptureHandler.capture(captureGatewayRequest);

        verify(gatewayClient).postRequestFor(eq(captureUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class));
        verify(gatewayClient).postRequestFor(eq(transferUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class));
        assertTrue(captureResponse.isSuccessful());
        assertThat(captureResponse.getFee().get(), is(51L));
    }
    
    public void shouldCaptureWithoutFee_ifCollectFeeSetToFalse() throws Exception {
        final String jsonResponse = load(STRIPE_CAPTURE_SUCCESS_RESPONSE);
        verify(gatewayClient).postRequestFor(eq(captureUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class));
        when(stripeGatewayConfig.isCollectFee()).thenReturn(false);

        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertTrue(response.isSuccessful());
        assertThat(response.state(), is(CaptureResponse.ChargeState.COMPLETE));
        assertThat(response.getTransactionId().isPresent(), is(true));
        assertThat(response.getTransactionId().get(), is("ch_123456"));
        assertThat(response.getFee().isPresent(), is(true));
        assertThat(response.getFee().get(), is(0L));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturns4xxHttpStatusCode() throws Exception {
        GatewayErrorException exception = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", load(STRIPE_ERROR_RESPONSE), SC_UNAUTHORIZED);
        when(gatewayClient.postRequestFor(eq(captureUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenThrow(exception);
        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.state(), is(nullValue()));
        assertThat(response.toString(), containsString("No such charge: ch_123456 or something similar"));
        assertThat(response.toString(), containsString("error code: resource_missing"));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturns5xxHttpStatusCode() throws Exception {
        GatewayErrorException exception = new GatewayErrorException("uh oh", "Problem with Stripe servers", INTERNAL_SERVER_ERROR_500);
        when(gatewayClient.postRequestFor(eq(captureUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenThrow(exception);

        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.state(), is(nullValue()));
        assertThat(response.getError().get().getMessage(), containsString("An internal server error occurred when capturing charge_external_id: " + captureGatewayRequest.getExternalId()));
        assertThat(response.getError().get().getErrorType(), is(GATEWAY_ERROR));
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
