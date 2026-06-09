package uk.gov.pay.connector.gateway.adyen.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonassert.JsonAssert;
import io.github.netmikey.logunit.api.LogCapturer;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.event.Level;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.AdyenIds;
import uk.gov.pay.connector.app.adyen.ApiKeys;
import uk.gov.pay.connector.app.adyen.BaseUrls;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasEntry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequestFixture.aCardAuthorisationGatewayRequest;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

@ExtendWith(MockitoExtension.class)
class AdyenAuthoriseHandlerTest {

    public static final AdyenCredentials ADYEN_CREDENTIALS = new AdyenCredentials(
            "legal_entity_id",
            "store_id",
            "account_holder_id",
            "balance_account_id");
    public static final String LIVE_ADYEN_CHECKOUT_BASE_URL = "https://example.com/live/v71";
    public static final String TEST_ADYEN_CHECKOUT_BASE_URL = "https://example.com/test/v71";
    private static final String TEST_API_KEY = "test-api-key"; // pragma: allowlist secret
    
    @Mock
    private GatewayClient mockClient;
    @Mock
    private ConnectorConfiguration mockConfig;
    private final JsonObjectMapper jsonObjectMapper = new JsonObjectMapper(new ObjectMapper());
    private AdyenAuthoriseHandler authoriseHandler;
    @Mock
    private GatewayClient.Response mockGatewayClientResponse;

    @Captor
    private ArgumentCaptor<GatewayClientPostRequest> captor;
    @RegisterExtension
    private final LogCapturer infoLogs = LogCapturer.create().captureForType(AdyenAuthoriseHandler.class, Level.INFO);

    @BeforeEach
    void setUp() {
        when(mockConfig.getLinks()).thenReturn(new LinksConfig());

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

        authoriseHandler = new AdyenAuthoriseHandler(mockClient, mockConfig, jsonObjectMapper);
    }

    @Test
    void should_send_request_to_Adyen_with_full_billing_address() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        givenAdyenReturnsASuccessResponse();

        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails()
                        .withAddress(makeFullBillingAddress())
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .build();
        authoriseHandler.authorise(authoriseRequest);

        then(mockClient).should().postRequestFor(captor.capture());
        String payload = captor.getValue().getGatewayOrder().getPayload();
        JsonAssert.with(payload).assertThat("$.billingAddress.houseNumberOrName", is("line1"));
        JsonAssert.with(payload).assertThat("$.billingAddress.street", is("line2"));
        JsonAssert.with(payload).assertThat("$.billingAddress.city", is("city"));
        JsonAssert.with(payload).assertThat("$.billingAddress.postalCode", is("postcode"));
        JsonAssert.with(payload).assertThat("$.billingAddress.country", is("country"));
    }

    @Test
    void should_send_request_to_Adyen_with_no_billing_address() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        givenAdyenReturnsASuccessResponse();

        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails()
                        .withAddress(null)
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .build();
        authoriseHandler.authorise(authoriseRequest);

        then(mockClient).should().postRequestFor(captor.capture());
        String payload = captor.getValue().getGatewayOrder().getPayload();
        JsonAssert.with(payload).assertNotDefined("$.billingAddress");
        var headers = captor.getValue().getHeaders();
        assertThat(headers, hasEntry("X-API-Key", TEST_API_KEY));
        assertThat(headers, hasEntry("Idempotency-Key", "authorise-" + authoriseRequest.getGovUkPayPaymentId()));
    }

    @Test
    void should_send_request_to_Adyen_with_partial_billing_address() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        givenAdyenReturnsASuccessResponse();

        var partialBillingAddress = new Address();
        partialBillingAddress.setLine1("line1");
        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails()
                        .withAddress(partialBillingAddress)
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .build();
        authoriseHandler.authorise(authoriseRequest);

        then(mockClient).should().postRequestFor(captor.capture());
        String payload = captor.getValue().getGatewayOrder().getPayload();
        JsonAssert.with(payload).assertEquals("$.billingAddress.houseNumberOrName", partialBillingAddress.getLine1());
        JsonAssert.with(payload).assertNotDefined("$.billingAddress.city");
        JsonAssert.with(payload).assertNotDefined("$.billingAddress.country");
        JsonAssert.with(payload).assertNotDefined("$.billingAddress.postalCode");
    }

    @Test
    void should_log_when_calling_Adyen_authorisation_of_charge() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        givenAdyenReturnsASuccessResponse();
        var request = aCardAuthorisationGatewayRequest()
                .withCredentials(ADYEN_CREDENTIALS)
                .build();

        authoriseHandler.authorise(request);

        infoLogs.assertContains("Calling Adyen for authorisation of charge");
    }

    @Test
    void should_return_a_GatewayResponse_for_successful_Authorisation() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        givenAdyenReturnsASuccessResponse();

        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails()
                        .withAddress(makeFullBillingAddress())
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .build();
        GatewayResponse<BaseAuthoriseResponse> response = authoriseHandler.authorise(authoriseRequest);

        assertThat(response.getBaseResponse().isPresent(), is(true));
        assertThat(response.getBaseResponse().get(), hasProperty("transactionId", equalTo("adyen-PSP-reference")));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
    }

    @Test
    void should_not_authorise_when_payment_provider_returns_unexpected_status_code() throws Exception {
        givenAdyenReturnsAnUnexpectedResponse();
        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails()
                        .withAddress(makeFullBillingAddress())
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .build();

        GatewayResponse<BaseAuthoriseResponse> response = authoriseHandler.authorise(authoriseRequest);

        assertThat(response.getGatewayError().isPresent(), Is.is(true));
        assertThat(response.getGatewayError().get().getMessage(),
                containsString("server error"));
        assertThat(response.getGatewayError().get().getErrorType(), Is.is(GATEWAY_ERROR));
    }

    private void givenAdyenReturnsASuccessResponse() throws GatewayException.GenericGatewayException, GatewayException.GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        when(mockClient.postRequestFor(any())).thenReturn(mockGatewayClientResponse);
        when(mockGatewayClientResponse.getEntity()).thenReturn("""
                {
                  "pspReference": "adyen-PSP-reference",
                  "resultCode": "Authorised",
                  "merchantReference": "merchant-reference"
                }
                """);
    }

    private void givenAdyenReturnsAnUnexpectedResponse() throws GatewayException.GenericGatewayException, GatewayException.GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        when(mockClient.postRequestFor(any()))
                .thenThrow(new GatewayException.GatewayErrorException("server error", """
                        {
                           "status": 500,
                           "errorCode": "905",
                           "message": "Payment details are not supported",
                           "errorType": "configuration",
                           "pspReference": "adyen-PSP-reference"
                         }
                        """, 500));
    }

    private static Address makeFullBillingAddress() {
        Address billingAddress = new Address();
        billingAddress.setLine1("line1");
        billingAddress.setLine2("line2");
        billingAddress.setCity("city");
        billingAddress.setCounty("county");
        billingAddress.setPostcode("postcode");
        billingAddress.setCountry("country");
        return billingAddress;
    }
}
