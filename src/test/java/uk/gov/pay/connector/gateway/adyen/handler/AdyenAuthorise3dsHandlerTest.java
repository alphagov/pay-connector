package uk.gov.pay.connector.gateway.adyen.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonassert.JsonAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.BaseUrls.CheckoutUrls;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.app.adyen.ApiKeysFixture.someApiKeys;
import static uk.gov.pay.connector.app.adyen.BaseUrlsFixture.someBaseUrls;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;

@ExtendWith(MockitoExtension.class)
class AdyenAuthorise3dsHandlerTest {

    private static final String TEST_ADYEN_CHECKOUT_BASE_URL = "https://example.com/test/someVersion";

    @Mock
    private GatewayClient mockClient;
    @Mock
    private ConnectorConfiguration mockConfig;
    @Mock
    private GatewayClient.Response mockGatewayClientResponse;
    @Captor
    private ArgumentCaptor<GatewayClientPostRequest> captor;

    private final JsonObjectMapper jsonObjectMapper = new JsonObjectMapper(new ObjectMapper());
    private AdyenAuthorise3dsHandler adyenAuthorise3dsHandler;

    @BeforeEach
    void setUp() {
        var baseUrls = someBaseUrls()
                .withCheckout(new CheckoutUrls(TEST_ADYEN_CHECKOUT_BASE_URL, "https://example.com/live/someVersion"))
                .build();

        var mockAdyenGatewayConfig = mock(AdyenGatewayConfig.class);
        lenient().when(mockAdyenGatewayConfig.getBaseUrls()).thenReturn(baseUrls);
        lenient().when(mockAdyenGatewayConfig.getApiKeys()).thenReturn(someApiKeys().build());
        when(mockConfig.getAdyenGatewayConfig()).thenReturn(mockAdyenGatewayConfig);

        adyenAuthorise3dsHandler = new AdyenAuthorise3dsHandler(mockClient, mockConfig, jsonObjectMapper);
    }

    @Test
    void should_send_redirect_result_to_adyen_payments_details_endpoint() throws Exception {
        when(mockClient.postRequestFor(any())).thenReturn(mockGatewayClientResponse);
        when(mockGatewayClientResponse.getEntity()).thenReturn(successResponse("Authorised"));
        var request = buildRequestWith("eyJ0cmFuc1N0YXR1cyI6IlkifQ==");

        adyenAuthorise3dsHandler.authorise3dsResponse(request);

        then(mockClient).should().postRequestFor(captor.capture());
        assertThat(captor.getValue().getUrl().toString(), is("https://example.com/test/someVersion/payments/details"));
        JsonAssert.with(captor.getValue().getGatewayOrder().getPayload())
                .assertThat("$.details.redirectResult", equalTo("eyJ0cmFuc1N0YXR1cyI6IlkifQ=="));
    }

    @ParameterizedTest
    @CsvSource({
            "Authorised,AUTHORISED",
            "Refused,REJECTED",
            "Cancelled,CANCELLED",
            "Error,ERROR",
            "ChallengeShopper,ERROR"
    })
    void should_map_adyen_result_code_to_authorisation_status(String resultCode, BaseAuthoriseResponse.AuthoriseStatus expectedStatus) throws Exception {
        when(mockClient.postRequestFor(any())).thenReturn(mockGatewayClientResponse);
        when(mockGatewayClientResponse.getEntity()).thenReturn(successResponse(resultCode));

        var response = adyenAuthorise3dsHandler.authorise3dsResponse(buildRequestWith("redirect-result"));

        assertThat(response.getMappedChargeStatus(), is(expectedStatus.getMappedChargeStatus()));
        assertThat(response.getTransactionId().isPresent(), is(true));
        assertThat(response.getTransactionId().get(), is("adyen-3ds-psp-reference"));
    }

    @Test
    void should_map_null_result_code_to_authorisation_status_error() throws Exception {
        when(mockClient.postRequestFor(any())).thenReturn(mockGatewayClientResponse);
        when(mockGatewayClientResponse.getEntity()).thenReturn(
                """
                        {
                          "pspReference": "adyen-3ds-psp-reference",
                          "resultCode": null
                        }
                        """
        );

        var response = adyenAuthorise3dsHandler.authorise3dsResponse(buildRequestWith("redirect-result"));

        assertThat(response.getMappedChargeStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.ERROR.getMappedChargeStatus()));
        assertThat(response.getTransactionId().isPresent(), is(true));
        assertThat(response.getTransactionId().get(), is("adyen-3ds-psp-reference"));
    }

    @Test
    void should_return_error_when_auth3ds_result_is_null() {
        var response = adyenAuthorise3dsHandler.authorise3dsResponse(
                Auth3dsResponseGatewayRequest.valueOf(aValidChargeEntity().withPaymentProvider("adyen").build(), null)
        );

        assertThat(response.getMappedChargeStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.ERROR.getMappedChargeStatus()));
        assertThat(response.getTransactionId().isPresent(), is(false));
    }

    @Test
    void should_return_exception_status_when_timeout_is_returned_from_gateway() throws Exception {
        when(mockClient.postRequestFor(any())).thenThrow(new GatewayException.GatewayConnectionTimeoutException("timeout"));

        var response = adyenAuthorise3dsHandler.authorise3dsResponse(buildRequestWith("redirect-result"));

        assertThat(response.isException(), is(true));
    }

    @Test
    void should_return_exception_status_when_4xx_5xx_is_returned_from_gateway() throws Exception {
        when(mockClient.postRequestFor(any())).thenThrow(new GatewayException.GatewayErrorException("non-success", """
                {
                  "status": 500,
                  "message": "failure",
                  "errorCode": "910",
                  "errorType": "internal",
                  "pspReference": "error-reference"
                }
                """, 500));

        var response = adyenAuthorise3dsHandler.authorise3dsResponse(buildRequestWith("redirect-result"));

        assertThat(response.isException(), is(true));
    }

    @Test
    void should_handle_non_parseable_error_response_and_return_exception_status() throws Exception {
        when(mockClient.postRequestFor(any())).thenThrow(new GatewayException.GatewayErrorException("non-success", "not-a-json", 502));

        var response = adyenAuthorise3dsHandler.authorise3dsResponse(buildRequestWith("redirect-result"));

        assertThat(response.isException(), is(true));
        assertThat(response.toString(), is("non-success"));
    }

    private Auth3dsResponseGatewayRequest buildRequestWith(String redirectResult) {
        var auth3dsResult = new Auth3dsResult();
        auth3dsResult.setRedirectResult(redirectResult);
        return Auth3dsResponseGatewayRequest.valueOf(
                aValidChargeEntity().withPaymentProvider("adyen").build(),
                auth3dsResult
        );
    }

    private String successResponse(String resultCode) {
        return """
                {
                  "pspReference": "adyen-3ds-psp-reference",
                  "resultCode": "%s"
                }
                """.formatted(resultCode);
    }
}
