package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeRefundHandler;
import uk.gov.pay.connector.gateway.stripe.request.StripeRefundRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferInRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferReversalRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.util.List;
import java.util.Map;

import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_REFUND_FULL_CHARGE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_TRANSFER_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class StripeRefundHandlerTest {
    private StripeRefundHandler refundHandler;
    private RefundGatewayRequest refundRequest;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());
    private GatewayAccountEntity gatewayAccount;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    @Mock
    private GatewayClient gatewayClient;
    @Mock
    private StripeGatewayConfig gatewayConfig;
    @Mock
    private Charge charge;

    @BeforeEach
    void setUp() throws Exception {
        refundHandler = new StripeRefundHandler(gatewayClient, gatewayConfig, objectMapper);

        gatewayAccount = aGatewayAccountEntity()
                .withId(123L)
                .withGatewayName(STRIPE.getName())
                .withRequires3ds(false)
                .withType(LIVE)
                .build();

        gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();

        gatewayAccount.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        RefundEntity refundEntity = RefundEntityFixture
                .aValidRefundEntity()
                .withAmount(100L)
                .build();
        when(charge.getGatewayTransactionId()).thenReturn("gatewayTransactionId");
        refundRequest = RefundGatewayRequest.valueOf(charge, refundEntity, gatewayAccount, gatewayAccountCredentialsEntity);
    }

    @Test
    void shouldRefundSuccessfully_usingPaymentIntentId() throws Exception {
        RefundEntity refundEntity = RefundEntityFixture
                .aValidRefundEntity()
                .withAmount(100L)
                .withGatewayTransactionId("pi_123")
                .build();
        refundRequest = RefundGatewayRequest.valueOf(charge, refundEntity, gatewayAccount, gatewayAccountCredentialsEntity);
        mockTransferSuccess();
        mockRefundSuccess();

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);

        assertNotNull(refund);
        assertTrue(refund.isSuccessful());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.COMPLETE));
        assertThat(refund.getReference().get(), is("re_1DRiccHj08j21DRiccHj08j2_test"));
    }

    @Test
    void shouldRefundSuccessfully_usingChargeId() throws Exception {
        mockTransferSuccess();
        mockRefundSuccess();

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);

        assertNotNull(refund);
        assertTrue(refund.isSuccessful());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.COMPLETE));
        assertThat(refund.getReference().get(), is("re_1DRiccHj08j21DRiccHj08j2_test"));
    }

    @Test
    void shouldNotRefund_whenStatusCode4xxOnRefund() throws Exception {
        mockRefund4xxResponse();

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);

        assertNotNull(refund);
        assertTrue(refund.getError().isPresent());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.ERROR));
        assertThat(refund.getError().get().getMessage(), Is.is("Stripe refund response (error code: resource_missing, error: No such charge: ch_123456 or something similar)"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    void shouldNotRefund_whenStatusCode5xxOnRefund() throws Exception {
        mockRefund5xxResponse();

        GatewayRefundResponse response = refundHandler.refund(refundRequest);

        assertThat(response.getError().isPresent(), Is.is(true));
        assertThat(response.getError().get().getMessage(), containsString("An internal server error occurred while refunding Transaction id:"));
        assertThat(response.getError().get().getErrorType(), Is.is(GATEWAY_ERROR));
    }

    @Test
    void shouldNotRefund_whenStatusCode4xxOnTransfer() throws Exception {
        mockRefundSuccess();
        mockTransfer4xxResponse();

        final GatewayRefundResponse refund = refundHandler.refund(refundRequest);

        assertNotNull(refund);
        assertTrue(refund.getError().isPresent());
        assertThat(refund.state(), is(GatewayRefundResponse.RefundState.ERROR));
        assertThat(refund.getError().get().getMessage(), Is.is("Stripe refund response (error code: resource_missing, error: No such charge: ch_123456 or something similar)"));
        assertThat(refund.getError().get().getErrorType(), Is.is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    void shouldNotRefund_whenStatusCode5xxOnTransfer() throws Exception {
        mockRefund5xxResponse();

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
        String errorMessage = load(STRIPE_ERROR_RESPONSE).replace("{{code}}", "resource_missing");
        GatewayErrorException gatewayClientException = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", errorMessage, 402);
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
        String errorMessage = load(STRIPE_ERROR_RESPONSE).replace("{{code}}", "resource_missing");
        GatewayErrorException gatewayClientException = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", errorMessage, 402);
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
