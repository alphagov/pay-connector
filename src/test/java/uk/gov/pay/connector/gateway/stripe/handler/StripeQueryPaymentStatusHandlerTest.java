package uk.gov.pay.connector.gateway.stripe.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.stripe.request.StripeQueryPaymentStatusRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_SEARCH_PAYMENT_INTENTS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class StripeQueryPaymentStatusHandlerTest {
    private StripeQueryPaymentStatusHandler handler;

    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    @Mock
    private GatewayClient gatewayClient;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());
    private GatewayAccountEntity gatewayAccount;
    private ChargeEntity chargeEntity;
    private ChargeQueryGatewayRequest queryGatewayRequest;
    private final GatewayClient.Response paymentSearchResponse = mock(GatewayClient.Response.class);

    @Before
    public void setUp() {
        handler = new StripeQueryPaymentStatusHandler(gatewayClient, stripeGatewayConfig, objectMapper);
        gatewayAccount = buildGatewayAccountEntity();
        when(paymentSearchResponse.getEntity()).thenReturn(searchResponse());
        chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId("transaction-id")
                .withAmount(10000L)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .build())
                .build();
        queryGatewayRequest = ChargeQueryGatewayRequest.valueOf(Charge.from(chargeEntity),
                chargeEntity.getGatewayAccount(), chargeEntity.getGatewayAccountCredentialsEntity());
    }

    @Test
    public void shouldRetrieveChargeCaptured_whenQueryingByMetadataAndStatusIsSucceeded() throws GatewayException {
        when(gatewayClient.getRequestFor(any(StripeQueryPaymentStatusRequest.class))).thenReturn(paymentSearchResponse);
        ChargeQueryResponse response = handler.queryPaymentStatus(queryGatewayRequest);
        assertThat(response.foundCharge(), is(true));
        assertThat(response.getMappedStatus().isPresent(), is(true));
        assertThat(response.getMappedStatus().get(), is(ChargeStatus.CAPTURED));
    }

    @Test
    public void shouldRetrieveChargeNotCaptured_whenQueryingByMetadataAndStatusIsRequiresPaymentMethod() throws GatewayException {
        when(gatewayClient.getRequestFor(any(StripeQueryPaymentStatusRequest.class))).thenReturn(paymentSearchResponse);
        when(paymentSearchResponse.getEntity()).thenReturn(searchResponse().replace("succeeded", "requires_payment_method"));
        ChargeQueryResponse response = handler.queryPaymentStatus(queryGatewayRequest);
        assertThat(response.foundCharge(), is(true));
        assertThat(response.getMappedStatus().isPresent(), is(true));
        assertThat(response.getMappedStatus().get(), is(ChargeStatus.AUTHORISATION_REJECTED));
    }

    @Test
    public void shouldRetrieveChargeNotCaptured_whenQueryingByMetadataAndStatusIsCanceled() throws GatewayException {
        when(gatewayClient.getRequestFor(any(StripeQueryPaymentStatusRequest.class))).thenReturn(paymentSearchResponse);
        when(paymentSearchResponse.getEntity()).thenReturn(searchResponse().replace("succeeded", "canceled"));
        ChargeQueryResponse response = handler.queryPaymentStatus(queryGatewayRequest);
        assertThat(response.foundCharge(), is(true));
        assertThat(response.getMappedStatus().isPresent(), is(true));
        assertThat(response.getMappedStatus().get(), is(ChargeStatus.AUTHORISATION_CANCELLED));
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void shouldThrowException_whenQueryingByMetadataFindsNoCharge() throws GatewayException {
        when(gatewayClient.getRequestFor(any(StripeQueryPaymentStatusRequest.class))).thenReturn(paymentSearchResponse);
        when(paymentSearchResponse.getEntity()).thenReturn("{\n" +
                "  \"object\": \"search_result\",\n" +
                "  \"url\": \"/v1/payment_intents/search\",\n" +
                "  \"has_more\": false,\n" +
                "  \"data\": []\n" +
                "}");
        handler.queryPaymentStatus(queryGatewayRequest);
    }

    @Test(expected = GatewayException.GatewayErrorException.class)
    public void shouldHandleGatewayErrorException_whenQueryingByMetadata() throws GatewayException {
        GatewayException.GatewayErrorException gatewayClientException = new GatewayException.GatewayErrorException("Unexpected HTTP status code 402 from gateway", load(STRIPE_ERROR_RESPONSE), 402);

        when(gatewayClient.getRequestFor(any(StripeQueryPaymentStatusRequest.class))).thenThrow(gatewayClientException);

        handler.queryPaymentStatus(queryGatewayRequest);
    }

    @Test(expected = GatewayException.GatewayConnectionTimeoutException.class)
    public void shouldHandleGatewayConnectionTimeoutException_whenQueryingByMetadata() throws GatewayException {
        GatewayException.GatewayConnectionTimeoutException gatewayClientException = new GatewayException.GatewayConnectionTimeoutException("Unexpected HTTP status code 418 from gateway");

        when(gatewayClient.getRequestFor(any(StripeQueryPaymentStatusRequest.class))).thenThrow(gatewayClientException);

        handler.queryPaymentStatus(queryGatewayRequest);
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

    private String searchResponse() {
        return load(STRIPE_SEARCH_PAYMENT_INTENTS_RESPONSE);
    }
}