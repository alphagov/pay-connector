package uk.gov.pay.connector.gateway.adyen.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonassert.JsonAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.AdyenIds;
import uk.gov.pay.connector.app.adyen.ApiKeys;
import uk.gov.pay.connector.app.adyen.BaseUrls;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClient.Response;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.RefundState.ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.RefundState.PENDING;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class AdyenRefundHandlerTest {

    private static final String TEST_CHECKOUT_BASE_URL = "https://checkout-test.example.com/v71";
    private static final String LIVE_CHECKOUT_BASE_URL = "https://checkout.example.com/v71";
    private static final String TEST_MERCHANT_ACCOUNT = "ADYEN_MERCHANT_ACCOUNT";
    private static final String PAYMENT_PSP_REFERENCE = "PSP-REF-123";
    private static final String REFUND_PSP_REFERENCE = "REFUND-PSP-REF-123";
    private static final String REFUND_EXTERNAL_ID = "refund-external-id";
    private static final String CHARGE_EXTERNAL_ID = "charge-external-id";
    private static final String STORE_ID = "store-id-123";
    private static final String TEST_API_KEY = "test-api-key"; // pragma: allowlist secret

    @Mock
    private GatewayClient mockClient;
    @Mock
    private ConnectorConfiguration mockConfig;
    @Mock
    private Response mockGatewayClientResponse;
    @Captor
    private ArgumentCaptor<GatewayClientPostRequest> captor;

    private AdyenRefundHandler refundHandler;
    private final JsonObjectMapper jsonObjectMapper = new JsonObjectMapper(new ObjectMapper());

    @BeforeEach
    void setUp() {
        BaseUrls mockBaseUrls = mock(BaseUrls.class);
        when(mockBaseUrls.checkout()).thenReturn(new BaseUrls.CheckoutUrls(TEST_CHECKOUT_BASE_URL, LIVE_CHECKOUT_BASE_URL));

        AdyenGatewayConfig mockAdyenGatewayConfig = mock(AdyenGatewayConfig.class);
        when(mockAdyenGatewayConfig.getMerchantAccountIds()).thenReturn(new AdyenIds(TEST_MERCHANT_ACCOUNT, "live-merchant-account"));
        when(mockAdyenGatewayConfig.getBaseUrls()).thenReturn(mockBaseUrls);

        ApiKeys mockApiKeys = mock(ApiKeys.class);
        ApiKeys.CompanyAccountApiKeys mockCompanyApiKeys = mock(ApiKeys.CompanyAccountApiKeys.class);
        when(mockApiKeys.companyAccount()).thenReturn(mockCompanyApiKeys);
        when(mockCompanyApiKeys.test()).thenReturn(TEST_API_KEY);
        when(mockAdyenGatewayConfig.getApiKeys()).thenReturn(mockApiKeys);

        when(mockConfig.getAdyenGatewayConfig()).thenReturn(mockAdyenGatewayConfig);

        refundHandler = new AdyenRefundHandler(mockClient, mockConfig, jsonObjectMapper);
    }

    @Test
    void shouldSendRefundRequestToCorrectAdyenEndpoint() throws Exception {
        when(mockClient.postRequestFor(any())).thenReturn(mockGatewayClientResponse);
        givenAdyenReturnsSuccessfulRefundResponse();

        refundHandler.refund(buildRefundGatewayRequest(1000L));

        then(mockClient).should().postRequestFor(captor.capture());
        assertThat(captor.getValue().getUrl().toString(),
                is(TEST_CHECKOUT_BASE_URL + "/payments/" + PAYMENT_PSP_REFERENCE + "/refunds"));
    }

    @Test
    void shouldSendCorrectPayloadToAdyenForFullRefund() throws Exception {
        when(mockClient.postRequestFor(any())).thenReturn(mockGatewayClientResponse);
        givenAdyenReturnsSuccessfulRefundResponse();

        refundHandler.refund(buildRefundGatewayRequest(1000L));

        then(mockClient).should().postRequestFor(captor.capture());
        String payload = captor.getValue().getGatewayOrder().getPayload();
        JsonAssert.with(payload)
                .assertThat("$.merchantAccount", is(TEST_MERCHANT_ACCOUNT))
                .assertThat("$.amount.value", is(1000))
                .assertThat("$.amount.currency", is("GBP"))
                .assertThat("$.reference", is(REFUND_EXTERNAL_ID))
                .assertThat("$.store", is(STORE_ID));
    }


    @Test
    void shouldReturnRefundSubmittedStateAndPspReferenceForSuccessfulFullRefund() throws Exception {
        when(mockClient.postRequestFor(any())).thenReturn(mockGatewayClientResponse);
        givenAdyenReturnsSuccessfulRefundResponse();

        GatewayRefundResponse response = refundHandler.refund(buildRefundGatewayRequest(1000L));

        assertThat(response.isSuccessful(), is(true));
        assertThat(response.state(), is(PENDING));
        assertThat(response.getReference().isPresent(), is(true));
        assertThat(response.getReference().get(), is(REFUND_PSP_REFERENCE));
    }

    @Test
    void shouldSendCorrectPayloadToAdyenForPartialRefund() throws Exception {
        when(mockClient.postRequestFor(any())).thenReturn(mockGatewayClientResponse);
        givenAdyenReturnsSuccessfulRefundResponse();

        refundHandler.refund(buildRefundGatewayRequest(100L));

        then(mockClient).should().postRequestFor(captor.capture());
        String payload = captor.getValue().getGatewayOrder().getPayload();
        JsonAssert.with(payload)
                .assertThat("$.merchantAccount", is(TEST_MERCHANT_ACCOUNT))
                .assertThat("$.amount.value", is(100))
                .assertThat("$.amount.currency", is("GBP"))
                .assertThat("$.reference", is(REFUND_EXTERNAL_ID))
                .assertThat("$.store", is(STORE_ID));
    }

    @Test
    void shouldReturnRefundSubmittedStateAndPspReferenceForSuccessfulPartialRefund() throws Exception {
        when(mockClient.postRequestFor(any())).thenReturn(mockGatewayClientResponse);
        givenAdyenReturnsSuccessfulRefundResponse();

        GatewayRefundResponse response = refundHandler.refund(buildRefundGatewayRequest(100L));

        assertThat(response.isSuccessful(), is(true));
        assertThat(response.state(), is(PENDING));
        assertThat(response.getReference().isPresent(), is(true));
        assertThat(response.getReference().get(), is(REFUND_PSP_REFERENCE));
    }

    @Test
    void shouldReturnRefundErrorStateWhenAdyenReturnsNon2xxResponse() throws Exception {
        var errorResponseBody = """
                {
                    "status": 403,
                    "errorCode": "901",
                    "message": "Invalid Merchant Account",
                    "errorType": "security"
                }""";
        when(mockClient.postRequestFor(any())).thenThrow(
                new GatewayException.GatewayErrorException("Non-success HTTP status code 403 from gateway", errorResponseBody, 403));

        GatewayRefundResponse response = refundHandler.refund(buildRefundGatewayRequest(100L));

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.state(), is(ERROR));
        assertThat(response.getError().isPresent(), is(true));
    }

    @Test
    void shouldReturnRefundErrorStateWhenAdyenResponseCannotBeDeserialised() throws Exception {
        when(mockClient.postRequestFor(any())).thenThrow(
                new GatewayException.GatewayErrorException("Non-success HTTP status code 500 from gateway", "not valid json", 500));

        GatewayRefundResponse response = refundHandler.refund(buildRefundGatewayRequest(100L));

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.state(), is(ERROR));
        assertThat(response.getError().isPresent(), is(true));
    }

    @Test
    void shouldReturnRefundErrorStateWhenRequestTimesOut() throws Exception {
        when(mockClient.postRequestFor(any())).thenThrow(
                new GatewayException.GatewayConnectionTimeoutException("Gateway connection timeout error"));

        GatewayRefundResponse response = refundHandler.refund(buildRefundGatewayRequest(100L));

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.state(), is(ERROR));
        assertThat(response.getError().isPresent(), is(true));
    }

    @Test
    void shouldReturnRefundErrorStateWhenGenericExceptionOccurs() throws Exception {
        when(mockClient.postRequestFor(any())).thenThrow(
                new GatewayException.GenericGatewayException("Unexpected error"));

        GatewayRefundResponse response = refundHandler.refund(buildRefundGatewayRequest(100L));

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.state(), is(ERROR));
        assertThat(response.getError().isPresent(), is(true));
    }

    private void givenAdyenReturnsSuccessfulRefundResponse() {
        when(mockGatewayClientResponse.getEntity()).thenReturn("""
                {
                    "merchantAccount": "GovernmentDigitalService_UK",
                    "paymentPspReference": "%s",
                    "pspReference": "%s",
                    "reference": "%s",
                    "status": "received"
                }""".formatted(PAYMENT_PSP_REFERENCE, REFUND_PSP_REFERENCE, REFUND_EXTERNAL_ID));
    }

    private RefundGatewayRequest buildRefundGatewayRequest(long amount) {
        var chargeEntity = aValidChargeEntity()
                .withExternalId(CHARGE_EXTERNAL_ID)
                .withGatewayTransactionId(PAYMENT_PSP_REFERENCE)
                .withAmount(1000L)
                .build();

        Charge charge = Charge.from(chargeEntity);

        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity()
                .withExternalId(REFUND_EXTERNAL_ID)
                .withAmount(amount)
                .build();

        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("store_id", STORE_ID))
                .withPaymentProvider(ADYEN.getName())
                .withState(ACTIVE)
                .build();

        var gatewayAccountEntity = aGatewayAccountEntity().build();

        return RefundGatewayRequest.valueOf(charge, refundEntity, gatewayAccountEntity, credentialsEntity);
    }
}
