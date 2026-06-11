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
import uk.gov.pay.connector.app.adyen.ApiKeys;
import uk.gov.pay.connector.app.adyen.BaseUrls;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;

@ExtendWith(MockitoExtension.class)
class AdyenAuthorise3dsHandlerTest {

    private static final String TEST_ADYEN_CHECKOUT_BASE_URL = "https://example.com/test/v71";

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
        BaseUrls mockBaseUrls = mock(BaseUrls.class);
        when(mockBaseUrls.checkout()).thenReturn(new BaseUrls.CheckoutUrls(TEST_ADYEN_CHECKOUT_BASE_URL, "https://example.com/live/v71"));
        AdyenGatewayConfig mockAdyenGatewayConfig = mock(AdyenGatewayConfig.class);
        when(mockAdyenGatewayConfig.getBaseUrls()).thenReturn(mockBaseUrls);

        ApiKeys mockApiKeys = mock(ApiKeys.class);
        ApiKeys.CompanyAccountApiKeys mockCompanyApiKeys = mock(ApiKeys.CompanyAccountApiKeys.class);
        when(mockApiKeys.companyAccount()).thenReturn(mockCompanyApiKeys);
        when(mockCompanyApiKeys.test()).thenReturn("test");
        when(mockAdyenGatewayConfig.getApiKeys()).thenReturn(mockApiKeys);
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
        assertThat(captor.getValue().getUrl().toString(), is("https://example.com/test/v71/payments/details"));
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
        assertThat(response.getTransactionId().get(), is("adyen-3ds-psp-reference"));
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
                  "resultCode": "%s",
                  "refusalReason": null,
                  "refusalReasonCode": null
                }
                """.formatted(resultCode);
    }
}
