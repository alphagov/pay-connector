package uk.gov.pay.connector.gateway.stripe;

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
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentCancelRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.List;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
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
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .withTransactionId(transactionId)
                .withAmount(10000L)
                .build();
    }

    @Test
    public void shouldCancelPaymentSuccessfully() throws Exception {
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(chargeEntity);
        final GatewayResponse<BaseCancelResponse> response = stripeCancelHandler.cancel(request);
        assertThat(response.isSuccessful(), is(true));
        verify(client).postRequestFor(any(StripeChargeCancelRequest.class));
    } 
    
    @Test
    public void shouldCancelPaymentSuccessfullyUsingPaymentIntents() throws Exception {
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(aValidChargeEntity()
                .withGatewayAccountEntity(buildGatewayAccountEntity())
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                                .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                                .withPaymentProvider(STRIPE.getName())
                                .withState(ACTIVE)
                                .build())
                .withTransactionId("pi_123")
                .withAmount(10000L)
                .build());
        final GatewayResponse<BaseCancelResponse> response = stripeCancelHandler.cancel(request);
        assertThat(response.isSuccessful(), is(true));
        verify(client).postRequestFor(any(StripePaymentIntentCancelRequest.class));
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
        var gatewayAccountEntity = aGatewayAccountEntity()
        .withId(1L)
        .withGatewayName("stripe")
        .withRequires3ds(false)
        .withType(TEST)
        .build();

        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        return gatewayAccountEntity;
    }
}
