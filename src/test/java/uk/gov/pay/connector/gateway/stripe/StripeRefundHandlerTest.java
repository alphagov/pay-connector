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
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeRefundHandler;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.Map;

import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
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
    private GatewayClient gatewayClient;
    @Mock
    private StripeGatewayConfig gatewayConfig;

    @Before
    public void setup() {
        refundHandler = new StripeRefundHandler(gatewayClient, gatewayConfig, objectMapper);
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().withAmount(100L).build();
        refundRequest = RefundGatewayRequest.valueOf(refundEntity);
        when(gatewayConfig.getAuthTokens()).thenReturn(mock(StripeAuthTokens.class));
        refundsUri = URI.create(gatewayConfig.getUrl() + "/v1/refunds");
    }

    @Test
    public void shouldRefundInFull() throws Exception {
        final String jsonResponse = load(STRIPE_REFUND_FULL_CHARGE_RESPONSE);
        GatewayClient.Response response = mock(GatewayClient.Response.class);
        when(response.getEntity()).thenReturn(jsonResponse);
        when(gatewayClient.postRequestFor(eq(refundsUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(response);

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);
        assertNotNull(refund);
        assertTrue(refund.isSuccessful());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.COMPLETE));
        assertThat(refund.getReference().get(), is("re_1DRiccHj08j21DRiccHj08j2_test"));
    }

    @Test
    public void shouldNotRefund_whenAmountIsMoreThanChargeAmount() throws Exception {
        GatewayErrorException gatewayClientException = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", 
                load(STRIPE_REFUND_ERROR_GREATER_AMOUNT_RESPONSE), 402);
        when(gatewayClient.postRequestFor(eq(refundsUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenThrow(gatewayClientException);

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);
        assertNotNull(refund);
        assertTrue(refund.getError().isPresent());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.ERROR));
        assertThat(refund.getError().get().getMessage(), is("Stripe refund response (error: Refund amount (£5.01) is greater than charge amount (£5.00))"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_anAlreadyRefundedCharge() throws Exception {
        GatewayErrorException gatewayClientException = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", 
                load(STRIPE_REFUND_ERROR_ALREADY_REFUNDED_RESPONSE), 402);
        when(gatewayClient.postRequestFor(eq(refundsUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenThrow(gatewayClientException);

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);

        assertNotNull(refund);
        assertTrue(refund.getError().isPresent());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.ERROR));
        assertThat(refund.getError().get().getMessage(), is("Stripe refund response (error: The transfer tr_blah_blah_blah is already fully reversed.)"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_whenStatusCode4xx() throws Exception {
        GatewayErrorException gatewayClientException = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", load(STRIPE_ERROR_RESPONSE), 402);
        when(gatewayClient.postRequestFor(eq(refundsUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenThrow(gatewayClientException);

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);
        assertNotNull(refund);
        assertTrue(refund.getError().isPresent());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.ERROR));
        assertThat(refund.getError().get().getMessage(), Is.is("Stripe refund response (error code: resource_missing, error: No such charge: ch_123456 or something similar)"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_whenStatusCode5xx() throws Exception {
        GatewayErrorException downstreamException = new GatewayErrorException("Problem with Stripe servers", "nginx problem", INTERNAL_SERVER_ERROR_500);
        when(gatewayClient.postRequestFor(eq(refundsUri), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenThrow(downstreamException);

        GatewayRefundResponse response = refundHandler.refund(refundRequest);
        assertThat(response.getError().isPresent(), Is.is(true));
        assertThat(response.getError().get().getMessage(), containsString("An internal server error occurred while refunding Transaction id:"));
        assertThat(response.getError().get().getErrorType(), Is.is(GATEWAY_ERROR));
    }
}
