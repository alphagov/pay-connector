package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeRefundHandler;
import uk.gov.pay.connector.gateway.stripe.request.StripeGetPaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeRefundRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferInRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferReversalRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_WITH_CHARGE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_REFUND_FULL_CHARGE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_TRANSFER_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class StripeRefundHandlerTest {
    private StripeRefundHandler refundHandler;
    private RefundGatewayRequest refundRequest;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());
    private GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();

    @Mock
    private GatewayClient gatewayClient;
    @Mock
    private StripeGatewayConfig gatewayConfig;

    @Before
    public void setUp() throws Exception {
        refundHandler = new StripeRefundHandler(gatewayClient, gatewayConfig, objectMapper);

        gatewayAccount.setId(123L);
        gatewayAccount.setCredentials(ImmutableMap.of("stripe_account_id", "stripe_account_id"));
        gatewayAccount.setType(GatewayAccountEntity.Type.LIVE);
        gatewayAccount.setGatewayName("stripe");

        RefundEntity refundEntity = RefundEntityFixture
                .aValidRefundEntity()
                .withAmount(100L)
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        refundRequest = RefundGatewayRequest.valueOf(refundEntity);

        GatewayClient.Response response = mock(GatewayClient.Response.class);
        when(response.getEntity()).thenReturn(load(STRIPE_PAYMENT_INTENT_WITH_CHARGE_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripeGetPaymentIntentRequest.class))).thenReturn(response);
    }

    @Test
    public void shouldRefundSuccessfully() throws Exception {
        mockTransferSuccess();
        mockRefundSuccess();

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);

        assertNotNull(refund);
        assertTrue(refund.isSuccessful());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.COMPLETE));
        assertThat(refund.getReference().get(), is("re_1DRiccHj08j21DRiccHj08j2_test"));
    }

    @Test
    public void shouldNotRefund_whenStatusCode4xxOnRefund() throws Exception {
        mockTransferSuccess();
        mockRefund4xxResponse();

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);

        assertNotNull(refund);
        assertTrue(refund.getError().isPresent());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.ERROR));
        assertThat(refund.getError().get().getMessage(), Is.is("Stripe refund response (error code: resource_missing, error: No such charge: ch_123456 or something similar)"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_whenStatusCode5xxOnRefund() throws Exception {
        mockTransferSuccess();
        mockRefund5xxResponse();

        GatewayRefundResponse response = refundHandler.refund(refundRequest);

        assertThat(response.getError().isPresent(), Is.is(true));
        assertThat(response.getError().get().getMessage(), containsString("An internal server error occurred while refunding Transaction id:"));
        assertThat(response.getError().get().getErrorType(), Is.is(GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_whenStatusCode4xxOnTransfer() throws Exception {
        mockTransfer4xxResponse();

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);

        assertNotNull(refund);
        assertTrue(refund.getError().isPresent());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.ERROR));
        assertThat(refund.getError().get().getMessage(), Is.is("Stripe refund response (error code: resource_missing, error: No such charge: ch_123456 or something similar)"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_whenStatusCode5xxOnTransfer() throws Exception {
        mockTransferResponse5xx();

        GatewayRefundResponse refund = refundHandler.refund(refundRequest);

        assertThat(refund.getError().isPresent(), Is.is(true));
        assertThat(refund.getError().get().getMessage(), containsString("An internal server error occurred while refunding Transaction id:"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GATEWAY_ERROR));
    }

    @Test
    public void shouldReverseTransfer_ifRefundStepFails() throws Exception {
        mockTransferSuccess();
        mockRefund5xxResponse();
        mockTransferReversalSuccess();

        GatewayRefundResponse refund = refundHandler.refund(refundRequest);

        assertThat(refund.getError().isPresent(), Is.is(true));
        assertThat(refund.getError().get().getMessage(), containsString("An internal server error occurred while refunding Transaction id:"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GATEWAY_ERROR));
    }

    @Test
    public void shouldNotRefund_ifTransferReversalFails() throws Exception {
        mockTransferSuccess();
        mockRefund4xxResponse();
        mockTransferReversalFailure();

        GatewayRefundResponse refund = refundHandler.refund(refundRequest);

        assertThat(refund.getError().isPresent(), Is.is(true));
        assertThat(refund.getError().get().getMessage(), containsString("An internal server error occurred while refunding Transaction id:"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GATEWAY_ERROR));
    }

    private void mockRefundSuccess() throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        GatewayClient.Response response = mock(GatewayClient.Response.class);
        when(response.getEntity()).thenReturn(load(STRIPE_REFUND_FULL_CHARGE_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripeRefundRequest.class))).thenReturn(response);
    }

    private void mockRefund4xxResponse() throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        GatewayErrorException gatewayClientException = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", load(STRIPE_ERROR_RESPONSE), 402);
        when(gatewayClient.postRequestFor(any(StripeRefundRequest.class))).thenThrow(gatewayClientException);
    }

    private void mockRefund5xxResponse() throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        GatewayErrorException downstreamException = new GatewayErrorException("Problem with Stripe servers", "nginx problem", INTERNAL_SERVER_ERROR_500);
        when(gatewayClient.postRequestFor(any(StripeRefundRequest.class))).thenThrow(downstreamException);
    }

    private void mockTransferSuccess() throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        GatewayClient.Response gatewayTransferResponse = mock(GatewayClient.Response.class);
        when(gatewayTransferResponse.getEntity()).thenReturn(load(STRIPE_TRANSFER_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripeTransferInRequest.class))).thenReturn(gatewayTransferResponse);
    }

    private void mockTransfer4xxResponse() throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        GatewayErrorException gatewayClientException = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", load(STRIPE_ERROR_RESPONSE), 402);
        when(gatewayClient.postRequestFor(any(StripeTransferInRequest.class))).thenThrow(gatewayClientException);
    }

    private void mockTransferResponse5xx() throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        GatewayErrorException downstreamException = new GatewayErrorException("Problem with Stripe servers", "nginx problem", INTERNAL_SERVER_ERROR_500);
        when(gatewayClient.postRequestFor(any(StripeTransferInRequest.class))).thenThrow(downstreamException);
    }

    private void mockTransferReversalSuccess() throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        GatewayClient.Response gatewayTransferReverseResponse = mock(GatewayClient.Response.class);
        when(gatewayClient.postRequestFor(any(StripeTransferReversalRequest.class))).thenReturn(gatewayTransferReverseResponse);
    }

    private void mockTransferReversalFailure() throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        GatewayErrorException downstreamException = new GatewayErrorException("Problem with Stripe servers", "nginx problem", INTERNAL_SERVER_ERROR_500);
        when(gatewayClient.postRequestFor(any(StripeTransferReversalRequest.class))).thenThrow(downstreamException);
    }
}
