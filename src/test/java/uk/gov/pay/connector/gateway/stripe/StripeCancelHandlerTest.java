package uk.gov.pay.connector.gateway.stripe;

import com.google.common.collect.ImmutableMap;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCancelHandler;
import uk.gov.pay.connector.gateway.stripe.request.StripeChargeCancelRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class StripeCancelHandlerTest {
    @Mock
    private GatewayClient client;
    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    
    @InjectMocks
    private StripeCancelHandler stripeCancelHandler;
    
    private ChargeEntity chargeEntity;

    @Before
    public void setup() {
        final String transactionId = "ch_1231231123123";
        chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(buildGatewayAccountEntity())
                .withTransactionId(transactionId)
                .withAmount(10000L)
                .build();

    }

    @Test
    public void shouldCancelPaymentSuccessfully() throws Exception {
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(chargeEntity);
        final GatewayResponse<BaseCancelResponse> response = stripeCancelHandler.cancel(request);
        assertThat(response.isSuccessful(), is(true));
    }

    @Test
    public void shouldHandle4xxFromStripeGateway() throws Exception {
        GatewayErrorException exception = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", load(STRIPE_ERROR_RESPONSE), SC_BAD_REQUEST);
        when(client.postRequestFor(any(StripeChargeCancelRequest.class))).thenThrow(exception);

        CancelGatewayRequest request = CancelGatewayRequest.valueOf(chargeEntity);

        final GatewayResponse<BaseCancelResponse> gatewayResponse = stripeCancelHandler.cancel(request);
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), Is.is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), Is.is("Unexpected HTTP status code 402 from gateway"));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), Is.is(GATEWAY_ERROR));
    }

    @Test
    public void shouldHandle5xxFromStripeGateway() throws Exception {
        GatewayErrorException exception = new GatewayErrorException("Problem with Stripe servers", "stripe server error", SC_SERVICE_UNAVAILABLE);
        when(client.postRequestFor(any(StripeChargeCancelRequest.class))).thenThrow(exception);

        CancelGatewayRequest request = CancelGatewayRequest.valueOf(chargeEntity);

        final GatewayResponse<BaseCancelResponse> gatewayResponse = stripeCancelHandler.cancel(request);
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), Is.is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), Is.is("Problem with Stripe servers"));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), Is.is(GATEWAY_ERROR));
    }

    @Test
    public void shouldHandleGatewayException() throws Exception {
        GatewayConnectionTimeoutException gatewayException = new GatewayConnectionTimeoutException("couldn't connect to https://stripe.url");
        when(client.postRequestFor(any(StripeChargeCancelRequest.class))).thenThrow(gatewayException);

        CancelGatewayRequest request = CancelGatewayRequest.valueOf(chargeEntity);

        final GatewayResponse<BaseCancelResponse> gatewayResponse = stripeCancelHandler.cancel(request);
        assertThat(gatewayResponse.isFailed(), is(true));
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
