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
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.AdyenIds;
import uk.gov.pay.connector.app.adyen.ApiKeys;
import uk.gov.pay.connector.app.adyen.BaseUrls;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.util.Map;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory.STORED_PAYMENT_METHOD_ID;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequestFixture.aCardAuthorisationGatewayRequest;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntityFixture.aPaymentInstrumentEntity;
import static uk.gov.service.payments.commons.model.AgreementPaymentType.RECURRING;

@ExtendWith(MockitoExtension.class)
class AdyenAuthoriseHandlerTest {

    public static final AdyenCredentials ADYEN_CREDENTIALS = new AdyenCredentials(
            "legal_entity_id",
            "store_id",
            "account_holder_id",
            "balance_account_id");
    public static final String LIVE_ADYEN_CHECKOUT_BASE_URL = "https://example.com/live/someVersion";
    public static final String TEST_ADYEN_CHECKOUT_BASE_URL = "https://example.com/test/someVersion";
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
    private final LogCapturer logger = LogCapturer.create().captureForType(AdyenAuthoriseHandler.class);

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
    void should_send_request_to_Adyen_with_api_key_and_idempotency_key_headers() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        givenAdyenReturnsASuccessResponse();

        var authoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails()
                        .withAddress(null)
                        .build())
                .withCredentials(ADYEN_CREDENTIALS)
                .build();
        authoriseHandler.authorise(authoriseRequest);

        then(mockClient).should().postRequestFor(captor.capture());
        var headers = captor.getValue().getHeaders();
        assertThat(headers, hasEntry("X-API-Key", TEST_API_KEY));
        assertThat(headers, hasEntry("Idempotency-Key", "authorise-" + authoriseRequest.getGovUkPayPaymentId()));
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

        logger.assertContains("Calling Adyen for authorisation of charge");
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

    @Test
    void should_send_request_to_Adyen_for_user_not_present_recurring_payment() throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        givenAdyenReturnsASuccessResponse();

        var agreementId = "some-agreement-id";
        var agreement = anAgreementEntity()
                .withExternalId(agreementId)
                .build();
        var storedPaymentMethodId = "42";
        var paymentInstrument = aPaymentInstrumentEntity()
                .withRecurringAuthToken(Map.of(
                        STORED_PAYMENT_METHOD_ID, storedPaymentMethodId))
                .build();

        var charge = setupChargeEntityForRecurringPayment(agreement, paymentInstrument);

        var authoriseRequest = RecurringPaymentAuthorisationGatewayRequest.valueOf(charge);
        authoriseHandler.authoriseUserNotPresent(authoriseRequest);

        then(mockClient).should().postRequestFor(captor.capture());
        var headers = captor.getValue().getHeaders();
        assertThat(headers, hasEntry("X-API-Key", TEST_API_KEY));
        assertThat(headers, hasEntry("Idempotency-Key", "authorise-" + authoriseRequest.getGovUkPayPaymentId()));

        String payload = captor.getValue().getGatewayOrder().getPayload();
        assertThat(payload, hasNoJsonPath("$.billingAddress.houseNumberOrName"));
        JsonAssert.with(payload).assertThat("$.paymentMethod.storedPaymentMethodId", is(storedPaymentMethodId));
        JsonAssert.with(payload).assertThat("$.paymentMethod.type", is("scheme"));

    }

    @Test
    void should_return_gateway_error_for_recurring_payment_when_adyen_returns_unexpected_status_code() throws Exception {
        givenAdyenReturnsAnUnexpectedResponse();
        var agreementId = "some-agreement-id";
        var agreement = anAgreementEntity()
                .withExternalId(agreementId)
                .build();
        var storedPaymentMethodId = "42";
        var paymentInstrument = aPaymentInstrumentEntity()
                .withRecurringAuthToken(Map.of(
                        STORED_PAYMENT_METHOD_ID, storedPaymentMethodId))
                .build();

        var charge = setupChargeEntityForRecurringPayment(agreement, paymentInstrument);

        var authoriseRequest = RecurringPaymentAuthorisationGatewayRequest.valueOf(charge);
        GatewayResponse<BaseAuthoriseResponse> response = authoriseHandler.authoriseUserNotPresent(authoriseRequest);

        assertThat(response.getGatewayError().isPresent(), Is.is(true));
        assertThat(response.getGatewayError().get().getMessage(),
                containsString("server error"));
        assertThat(response.getGatewayError().get().getErrorType(), Is.is(GATEWAY_ERROR));

        logger.assertContains("GatewayException occurred when authorising user not present payment");
    }

    private static ChargeEntity setupChargeEntityForRecurringPayment(AgreementEntity agreement, PaymentInstrumentEntity paymentInstrument) {
        var gatewayAccountCredential = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of(
                        "legal_entity_id", "legal_entity_id",
                        "store_id", "store_id",
                        "account_holder_id", "account_holder_id",
                        "balance_account_id", "balance_account_id"))
                .withPaymentProvider(ADYEN.getName())
                .build();

        return aValidChargeEntity()
                .withAgreementPaymentType(RECURRING)
                .withAgreementEntity(agreement)
                .withPaymentInstrument(paymentInstrument)
                .withGatewayAccountCredentialsEntity(gatewayAccountCredential)
                .build();
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
