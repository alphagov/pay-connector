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
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdyenCaptureHandlerTest {
    public static final String LIVE_ADYEN_CHECKOUT_BASE_URL = "https://example.com/live/v71";
    public static final String TEST_ADYEN_CHECKOUT_BASE_URL = "https://example.com/test/v71";
    private static final String TEST_API_KEY = "test-api-key"; // pragma: allowlist secret
    @Mock
    private GatewayClient mockClient;
    @Mock
    private ConnectorConfiguration mockConfig;
    private final JsonObjectMapper jsonObjectMapper = new JsonObjectMapper(new ObjectMapper());
    private AdyenCaptureHandler captureHandler;
    @Mock
    private GatewayClient.Response mockGatewayClientResponse;
    @Captor
    private ArgumentCaptor<GatewayClientPostRequest> captor;

    @BeforeEach
    void setUp() {
        BaseUrls mockBaseUrls = mock(BaseUrls.class);
        when(mockBaseUrls.checkout()).thenReturn(new BaseUrls.CheckoutUrls(TEST_ADYEN_CHECKOUT_BASE_URL, LIVE_ADYEN_CHECKOUT_BASE_URL));
        AdyenGatewayConfig mockAdyenGatewayConfig = mock(AdyenGatewayConfig.class);
        when(mockAdyenGatewayConfig.getMerchantAccountIds()).thenReturn(new AdyenIds("test", "live"));
        when(mockAdyenGatewayConfig.getBaseUrls()).thenReturn(mockBaseUrls);

        ApiKeys mockApiKeys = mock(ApiKeys.class);
        ApiKeys.CompanyAccountApiKeys mockCompanyApiKeys = mock(ApiKeys.CompanyAccountApiKeys.class);
        when(mockApiKeys.companyAccount()).thenReturn(mockCompanyApiKeys);
        when(mockCompanyApiKeys.test()).thenReturn(TEST_API_KEY);
        when(mockAdyenGatewayConfig.getApiKeys()).thenReturn(mockApiKeys);
        when(mockConfig.getAdyenGatewayConfig()).thenReturn(mockAdyenGatewayConfig);

        captureHandler = new AdyenCaptureHandler(mockClient, mockConfig, jsonObjectMapper);
    }

    @Test
    void should_send_a_capture_request_for_an_authorised_payment() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        when(mockClient.postRequestFor(any())).thenReturn(mockGatewayClientResponse);
        givenAdyenReturnsASuccessResponse();

        CaptureGatewayRequest captureRequest = createCaptureRequest();
        captureHandler.capture(captureRequest);

        then(mockClient).should().postRequestFor(captor.capture());
        String payload = captor.getValue().getGatewayOrder().getPayload();
        JsonAssert.with(payload).assertThat("$.merchantAccount", is("test"));
        JsonAssert.with(payload).assertThat("$.amount.value", is(500));
        JsonAssert.with(payload).assertThat("$.amount.currency", is("GBP"));
    }

    @Test
    void should_send_a_capture_request_with_api_key_and_idempotency_key_headers() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        when(mockClient.postRequestFor(any())).thenReturn(mockGatewayClientResponse);
        givenAdyenReturnsASuccessResponse();

        CaptureGatewayRequest captureRequest = createCaptureRequest();
        captureHandler.capture(captureRequest);

        then(mockClient).should().postRequestFor(captor.capture());
        String payload = captor.getValue().getGatewayOrder().getPayload();
        JsonAssert.with(payload).assertThat("$.merchantAccount", is("test"));
        JsonAssert.with(payload).assertThat("$.amount.value", is(500));
        JsonAssert.with(payload).assertThat("$.amount.currency", is("GBP"));
        var headers = captor.getValue().getHeaders();
        assertThat(headers, hasEntry("X-API-Key", TEST_API_KEY));
        assertThat(headers, hasEntry("Idempotency-Key", "capture-" + captureRequest.getExternalId()));
    }

    @Test
    void should_return_a_capture_response_with_a_charge_state_of_PENDING() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        when(mockClient.postRequestFor(any())).thenReturn(mockGatewayClientResponse);
        givenAdyenReturnsASuccessResponse();

        CaptureGatewayRequest captureRequest = createCaptureRequest();
        var response = captureHandler.capture(captureRequest);

        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getTransactionId().isPresent(), is(true));
        assertThat(response.getTransactionId().get(), is("gateway-transaction-id"));
        assertThat(response.state(), is(CaptureResponse.ChargeState.PENDING));
    }

    @Test
    void should_return_CaptureResponse_when_Adyen_returns_GatewayException() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        var errorResponseBody = """
                {
                           "status": 403,
                           "errorCode": "901",
                           "message": "Invalid Merchant Account",
                           "errorType": "security",
                           "pspReference": "capture-psp-reference"
                         }""";
        when(mockClient.postRequestFor(any())).thenThrow(errorFromAdyen("client error", errorResponseBody, 403));

        CaptureGatewayRequest captureRequest = createCaptureRequest();
        var response = captureHandler.capture(captureRequest);

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getErrorMessage().isPresent(), is(true));
        assertNull(response.state());
        assertThat(response.getTransactionId().isPresent(), is(true));
        assertThat(response.getTransactionId().get(), is("gateway-transaction-id"));
        assertThat(response.getErrorMessage().get(), equalTo("Adyen capture response(status: 403, errorMessage: Invalid Merchant Account)"));
    }

    @Test
    void should_return_CaptureResponse_when_Adyen_returns_GenericError() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        when(mockClient.postRequestFor(any())).thenThrow(errorFromAdyen("server error", null, 500));

        CaptureGatewayRequest captureRequest = createCaptureRequest();
        var response = captureHandler.capture(captureRequest);

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getErrorMessage().isPresent(), is(true));
        assertNull(response.state());
        assertThat(response.getTransactionId().isPresent(), is(false));
        assertThat(response.getErrorMessage().get(), equalTo("server error"));
    }

    @Test
    void should_return_CaptureResponse_when_AdyenError_cannot_be_deserialised() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        when(mockClient.postRequestFor(any())).thenThrow(errorFromAdyen("unknown error", "ERROR", 500));

        CaptureGatewayRequest captureRequest = createCaptureRequest();
        var response = captureHandler.capture(captureRequest);

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getErrorMessage().isPresent(), is(true));
        assertNull(response.state());
        assertThat(response.getTransactionId().isPresent(), is(false));
        assertThat(response.getErrorMessage().get(), equalTo("unknown error"));
    }

    private CaptureGatewayRequest createCaptureRequest() {
        var charge = ChargeEntityFixture.aValidChargeEntity()
                .withGatewayTransactionId("gateway-transaction-id")
                .withExternalId("gateway-external-id")
                .withAmount(500L)
                .build();

        return CaptureGatewayRequest.valueOf(charge);
    }

    private void givenAdyenReturnsASuccessResponse() {
        when(mockGatewayClientResponse.getEntity()).thenReturn("""
                {
                   "merchantAccount": "gov merchant account",
                   "paymentPspReference": "gateway-transaction-id",
                   "pspReference": "capture-psp-reference",
                   "status": "received",
                   "amount": {
                     "value": 1000,
                     "currency": "GBP"
                   }
                 }
                """);
    }

    private GatewayException errorFromAdyen(String errorMessage, String responseBody, int status) {
        return responseBody == null
                ? new GatewayException.GenericGatewayException(errorMessage)
                : new GatewayException.GatewayErrorException(errorMessage, responseBody, status);
    }

}
