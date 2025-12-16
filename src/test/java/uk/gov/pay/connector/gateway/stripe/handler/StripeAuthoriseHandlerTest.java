package uk.gov.pay.connector.gateway.stripe.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RecurringPaymentAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.request.*;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsRequiredParams;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayPaymentInfo;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayPaymentInfoFixture.anApplePayPaymentInfo;
import static uk.gov.pay.connector.model.domain.googlepay.GooglePayPaymentInfoFixture.aGooglePayPaymentInfo;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntityFixture.aPaymentInstrumentEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CUSTOMER_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_REQUIRES_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE_WITH_CUSTOMER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_METHOD_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_TOKEN_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
public class StripeAuthoriseHandlerTest {
    private static final String CARD_HOLDER = "Mr. Payment";
    public static final String CUSTOMER_ID = "cus_abc213";
    public static final String PAYMENT_METHOD_ID = "pm_abc123";
    private static final String TOKEN_ID = "token_abc_123";
    @Captor
    private ArgumentCaptor<StripePostRequest> stripePostRequestCaptor;
    private StripeAuthoriseHandler handler;
    @Mock
    private GatewayClient gatewayClient;
    @Mock
    private ConnectorConfiguration configuration;
    @Mock
    private StripeGatewayConfig gatewayConfig;
    @Mock
    private LinksConfig linksConfig = mock(LinksConfig.class);
    @Mock
    private GatewayClient.Response customerResponse;
    @Mock
    private GatewayClient.Response paymentMethodResponse;
    @Mock
    private GatewayClient.Response paymentIntentsResponse;
    @Mock
    private GatewayClient.Response tokenResponse;
    private final JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());

    @BeforeEach
    void setUp() {
        when(configuration.getLinks()).thenReturn(linksConfig);
        when(linksConfig.getFrontendUrl()).thenReturn("http://frontendUrl");
        handler = new StripeAuthoriseHandler(gatewayClient, gatewayConfig, configuration, objectMapper);
    }

    @Nested
    class Authorisation {
        @Test
        void shouldAuthoriseImmediately_whenPaymentIntentReturnsAsRequiresCapture() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);
            when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());
            when(paymentIntentsResponse.getEntity()).thenReturn(successCreatePaymentIntentsResponse());

            GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
            gatewayAccount.setIntegrationVersion3ds(2);
            ChargeEntity charge = buildTestCharge(gatewayAccount);
            GatewayResponse<BaseAuthoriseResponse> response = handler.authorise(buildTestAuthorisationRequest(charge));
            
            assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
            assertTrue(response.getBaseResponse().isPresent());
            assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_1FHESeEZsufgnuO08A2FUSPy"));
            assertThat(response.getBaseResponse().get().getCardExpiryDate().isPresent(), is(true));
            assertThat(response.getBaseResponse().get().getCardExpiryDate().get().getTwoDigitMonth(), is("08"));
            assertThat(response.getBaseResponse().get().getCardExpiryDate().get().getTwoDigitYear(), is("24"));
        }

        @Test
        void shouldAuthorise_ForAddressInUs() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);
            when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());
            when(paymentIntentsResponse.getEntity()).thenReturn(successCreatePaymentIntentsResponse());

            GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
            gatewayAccount.setIntegrationVersion3ds(2);
            ChargeEntity charge = buildTestCharge(gatewayAccount);
            GatewayResponse<BaseAuthoriseResponse> response = handler.authorise(buildTestUsAuthorisationRequest(charge));

            assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
            assertTrue(response.getBaseResponse().isPresent());
            assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_1FHESeEZsufgnuO08A2FUSPy"));
        }

        @Test
        void shouldAuthoriseSetUpRecurringPaymentAgreement() throws Exception {
            final var agreementDescription = "an agreement description";

            when(gatewayClient.postRequestFor(any(StripeCustomerRequest.class))).thenReturn(customerResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);
            when(customerResponse.getEntity()).thenReturn(successCreateCustomerResponse());
            when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());
            when(paymentIntentsResponse.getEntity()).thenReturn(successCreatePaymentIntentResponseWithCustomer());

            GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
            ChargeEntity charge = buildTestChargeToSetUpAgreement(gatewayAccount, agreementDescription);
            GatewayResponse<BaseAuthoriseResponse> response = handler.authorise(buildTestAuthorisationRequest(charge));

            verify(gatewayClient, times(3)).postRequestFor(stripePostRequestCaptor.capture());
            var customerRequest = (StripeCustomerRequest) (stripePostRequestCaptor.getAllValues().get(1));
            var paymentIntentRequest = (StripePaymentIntentRequest) (stripePostRequestCaptor.getAllValues().get(2));

            assertThat(customerRequest.getName(), is(CARD_HOLDER));
            assertThat(customerRequest.getDescription(), is(agreementDescription));
            assertThat(paymentIntentRequest.getCustomerId(), is("cus_4QFOF3xrvBT3nU"));
            assertThat(response.getBaseResponse().get().getGatewayRecurringAuthToken().isPresent(), is(true));
            assertThat(response.getBaseResponse().get().getGatewayRecurringAuthToken().get().get(STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY), is("cus_4QFOF3xrvBT3nU"));
            assertThat(response.getBaseResponse().get().getGatewayRecurringAuthToken().get().get(STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY), is("pm_1FHESdEZsufgnuO0bzghQoZ2"));
            assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
            assertTrue(response.getBaseResponse().isPresent());
            assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_1FHESeEZsufgnuO08A2FUSPy"));
        }

        @Test
        void shouldSetAs3DSRequired_whenPaymentIntentReturnsWithRequiresAction() throws Exception {
            when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());
            when(paymentIntentsResponse.getEntity()).thenReturn(requires3DSCreatePaymentIntentsResponse());
            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);

            GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
            gatewayAccount.setIntegrationVersion3ds(2);
            ChargeEntity charge = buildTestCharge(gatewayAccount);
            GatewayResponse<BaseAuthoriseResponse> response = handler.authorise(buildTestAuthorisationRequest(charge));

            assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));
            assertTrue(response.getBaseResponse().isPresent());
            assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_123")); // id from templates/stripe/create_payment_intent_requires_3ds_response.json

            Optional<Stripe3dsRequiredParams> stripeParamsFor3ds = (Optional<Stripe3dsRequiredParams>) response.getBaseResponse().get().getGatewayParamsFor3ds();
            assertThat(stripeParamsFor3ds.isPresent(), is(true));
            assertThat(stripeParamsFor3ds.get().toAuth3dsRequiredEntity().getIssuerUrl(), containsString("https://hooks.stripe.com"));
        }

        @Test
        void shouldNotAuthorise_whenProcessingExceptionIsThrown() throws Exception {

            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                    .thenThrow(new GatewayConnectionTimeoutException("jakarta.ws.rs.ProcessingException: java.io.IOException"));
            when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());

            ChargeEntity charge = buildTestCharge();
            GatewayResponse<BaseAuthoriseResponse> authoriseResponse = handler.authorise(buildTestAuthorisationRequest(charge));

            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertEquals("jakarta.ws.rs.ProcessingException: java.io.IOException",
                    authoriseResponse.getGatewayError().get().getMessage());
            assertEquals(GATEWAY_CONNECTION_TIMEOUT_ERROR, authoriseResponse.getGatewayError().get().getErrorType());
        }

        @Test
        void shouldMarkChargeAsAuthorisationRejected_whenStripeRespondsWithErrorTypeCardError() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse("card_error"), 400));
            when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());

            ChargeEntity charge = buildTestCharge();
            GatewayResponse<BaseAuthoriseResponse> authoriseResponse = handler.authorise(buildTestAuthorisationRequest(charge));
            assertThat(authoriseResponse.getBaseResponse().isPresent(), is(true));

            BaseAuthoriseResponse baseAuthoriseResponse = authoriseResponse.getBaseResponse().get();

            assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_REJECTED));
            assertThat(baseAuthoriseResponse.getTransactionId(), equalTo("pi_aaaaaaaaaaaaaaaaaaaaaaaa"));
            assertThat(baseAuthoriseResponse.toString(), containsString("type: card_error"));
            assertThat(baseAuthoriseResponse.toString(), containsString("message: No such charge: ch_123456 or something similar"));
            assertThat(baseAuthoriseResponse.toString(), containsString("code: resource_missing"));
        }

        @Test
        void shouldMarkChargeAsAuthorisationRejected_whenStripeRespondsWithErrorTypeInvalidRequestErrorAndErrorCodeCardDecline() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse("invalid_request_error", "card_decline_rate_limit_exceeded"), 400));
            when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());

            ChargeEntity charge = buildTestCharge();
            GatewayResponse<BaseAuthoriseResponse> authoriseResponse = handler.authorise(buildTestAuthorisationRequest(charge));
            assertThat(authoriseResponse.getBaseResponse().isPresent(), is(true));

            BaseAuthoriseResponse baseAuthoriseResponse = authoriseResponse.getBaseResponse().get();

            assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_REJECTED));
            assertThat(baseAuthoriseResponse.getTransactionId(), equalTo("pi_aaaaaaaaaaaaaaaaaaaaaaaa"));
            assertThat(baseAuthoriseResponse.toString(), containsString("type: invalid_request_error"));
            assertThat(baseAuthoriseResponse.toString(), containsString("message: No such charge: ch_123456 or something similar"));
            assertThat(baseAuthoriseResponse.toString(), containsString("code: card_decline_rate_limit_exceeded"));
        }

        @Test
        void shouldMarkChargeAsAuthorisationError_whenStripeRespondsWithErrorTypeInvalidRequestAndErrorCodeIsNotCardDecline() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse("invalid_request_error"), 400));
            when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());

            ChargeEntity charge = buildTestCharge();
            GatewayResponse<BaseAuthoriseResponse> authoriseResponse = handler.authorise(buildTestAuthorisationRequest(charge));
            assertThat(authoriseResponse.getBaseResponse().isPresent(), is(true));

            BaseAuthoriseResponse baseAuthoriseResponse = authoriseResponse.getBaseResponse().get();

            assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_ERROR));
            assertThat(baseAuthoriseResponse.getTransactionId(), equalTo("pi_aaaaaaaaaaaaaaaaaaaaaaaa"));
            assertThat(baseAuthoriseResponse.toString(), containsString("type: invalid_request_error"));
            assertThat(baseAuthoriseResponse.toString(), containsString("message: No such charge: ch_123456 or something similar"));
            assertThat(baseAuthoriseResponse.toString(), containsString("code: resource_missing"));
        }

        @Test
        void shouldMarkChargeAsAuthorisationError_whenStripeRespondsWithErrorTypeOtherThanCardError() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse("api_error"), 400));
            when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());

            ChargeEntity charge = buildTestCharge();
            GatewayResponse<BaseAuthoriseResponse> authoriseResponse = handler.authorise(buildTestAuthorisationRequest(charge));
            assertThat(authoriseResponse.getBaseResponse().isPresent(), is(true));

            BaseAuthoriseResponse baseAuthoriseResponse = authoriseResponse.getBaseResponse().get();
            assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_ERROR));
            assertThat(baseAuthoriseResponse.toString(), containsString("type: api_error"));
        }

        @Test
        void shouldNotAuthorise_whenPaymentProviderReturnsUnexpectedStatusCodeFromCreatePaymentMethod() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse(), 500));

            ChargeEntity charge = buildTestCharge();
            GatewayResponse<BaseAuthoriseResponse> authoriseResponse = handler.authorise(buildTestAuthorisationRequest(charge));

            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertThat(authoriseResponse.getGatewayError().get().getMessage(),
                    containsString("There was an internal server error"));
            assertThat(authoriseResponse.getGatewayError().get().getErrorType(), is(GATEWAY_ERROR));
        }

        @Test
        void shouldNotAuthorise_whenPaymentProviderReturnsUnexpectedStatusCodeFromCreatePaymentIntent() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse(), 500));
            when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());

            ChargeEntity charge = buildTestCharge();
            GatewayResponse<BaseAuthoriseResponse> authoriseResponse = handler.authorise(buildTestAuthorisationRequest(charge));

            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertThat(authoriseResponse.getGatewayError().get().getMessage(),
                    containsString("There was an internal server error"));
            assertThat(authoriseResponse.getGatewayError().get().getErrorType(), is(GATEWAY_ERROR));
        }

        @Test
        void shouldNotAuthoriseRecurringPaymentAgreement_whenPaymentProviderReturnsUnexpectedStatusCodeFromCreateCustomer() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
            when(gatewayClient.postRequestFor(any(StripeCustomerRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse(), 500));
            when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());

            ChargeEntity charge = buildTestChargeToSetUpAgreement(buildTestGatewayAccountEntity(), "agreement description");
            GatewayResponse<BaseAuthoriseResponse> authoriseResponse = handler.authorise(buildTestAuthorisationRequest(charge));

            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertThat(authoriseResponse.getGatewayError().get().getMessage(),
                    containsString("There was an internal server error"));
            assertThat(authoriseResponse.getGatewayError().get().getErrorType(), is(GATEWAY_ERROR));
        }

        @Test
        void shouldNotAuthoriseRecurringPaymentAgreement_whenPaymentProviderReturnsUnexpectedStatusCodeFromCreatePaymentIntent() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
            when(gatewayClient.postRequestFor(any(StripeCustomerRequest.class))).thenReturn(customerResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse(), 500));
            when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());
            when(customerResponse.getEntity()).thenReturn(successCreateCustomerResponse());

            ChargeEntity charge = buildTestChargeToSetUpAgreement(buildTestGatewayAccountEntity(), "agreement description");
            GatewayResponse<BaseAuthoriseResponse> authoriseResponse = handler.authorise(buildTestAuthorisationRequest(charge));

            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertThat(authoriseResponse.getGatewayError().get().getMessage(),
                    containsString("There was an internal server error"));
            assertThat(authoriseResponse.getGatewayError().get().getErrorType(), is(GATEWAY_ERROR));
        }
    }

    @Nested
    class AuthoriseUserNotPresent {
        @Test
        void shouldAuthoriseUserNotPresentPayment() throws Exception {

            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);
            when(paymentIntentsResponse.getEntity()).thenReturn(successCreatePaymentIntentResponseWithCustomer());
            ChargeEntity charge = buildTestAuthorisationModeAgreementCharge();
            RecurringPaymentAuthorisationGatewayRequest authRequest = RecurringPaymentAuthorisationGatewayRequest.valueOf(charge);
            GatewayResponse<BaseAuthoriseResponse> response = handler.authoriseUserNotPresent(authRequest);

            verify(gatewayClient).postRequestFor(stripePostRequestCaptor.capture());
            var paymentIntentRequest = (StripePaymentIntentRequest) (stripePostRequestCaptor.getValue());

            assertThat(paymentIntentRequest.getCustomerId(), is(CUSTOMER_ID));
            assertThat(paymentIntentRequest.getPaymentMethodId(), is(PAYMENT_METHOD_ID));

            assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
            assertTrue(response.getBaseResponse().isPresent());
            assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_1FHESeEZsufgnuO08A2FUSPy"));
        }

        @Test
        void shouldMarkChargeAsAuthorisationRejected_forUserNotPresentPayment_whenStripeRespondsWithErrorTypeCardError() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse("card_error"), 400));

            ChargeEntity charge = buildTestAuthorisationModeAgreementCharge();
            RecurringPaymentAuthorisationGatewayRequest authRequest = RecurringPaymentAuthorisationGatewayRequest.valueOf(charge);
            GatewayResponse<BaseAuthoriseResponse> authoriseResponse = handler.authoriseUserNotPresent(authRequest);
            assertThat(authoriseResponse.getBaseResponse().isPresent(), is(true));

            BaseAuthoriseResponse baseAuthoriseResponse = authoriseResponse.getBaseResponse().get();
            assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_REJECTED));
            assertThat(baseAuthoriseResponse.getTransactionId(), equalTo("pi_aaaaaaaaaaaaaaaaaaaaaaaa"));
            assertThat(baseAuthoriseResponse.toString(), containsString("type: card_error"));
            assertThat(baseAuthoriseResponse.toString(), containsString("message: No such charge: ch_123456 or something similar"));
            assertThat(baseAuthoriseResponse.toString(), containsString("code: resource_missing"));
        }

        @Test
        void shouldNotAuthorise_forUserNotPresentPayment_WhenPaymentProviderReturnsUnexpectedStatusCodeFromCreatePaymentIntent() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse(), 500));

            ChargeEntity charge = buildTestAuthorisationModeAgreementCharge();
            RecurringPaymentAuthorisationGatewayRequest authRequest = RecurringPaymentAuthorisationGatewayRequest.valueOf(charge);
            GatewayResponse<BaseAuthoriseResponse> authoriseResponse = handler.authoriseUserNotPresent(authRequest);

            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertThat(authoriseResponse.getGatewayError().get().getMessage(),
                    containsString("There was an internal server error"));
            assertThat(authoriseResponse.getGatewayError().get().getErrorType(), is(GATEWAY_ERROR));
        }

        @Test
        void shouldNotAuthorise_forUserNotPresentPayment_whenProcessingExceptionIsThrown() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                    .thenThrow(new GatewayConnectionTimeoutException("jakarta.ws.rs.ProcessingException: java.io.IOException"));

            ChargeEntity charge = buildTestAuthorisationModeAgreementCharge();
            RecurringPaymentAuthorisationGatewayRequest authRequest = RecurringPaymentAuthorisationGatewayRequest.valueOf(charge);
            GatewayResponse<BaseAuthoriseResponse> authoriseResponse = handler.authoriseUserNotPresent(authRequest);

            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
            assertEquals("jakarta.ws.rs.ProcessingException: java.io.IOException",
                    authoriseResponse.getGatewayError().get().getMessage());
            assertEquals(GATEWAY_CONNECTION_TIMEOUT_ERROR, authoriseResponse.getGatewayError().get().getErrorType());
        }
    }

    @Nested
    class AuthoriseWallet {
        @Test
        void shouldAuthoriseApplePayPayment() throws Exception {
            when(gatewayClient.postRequestFor(any(StripeTokenRequest.class))).thenReturn(tokenResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);
            when(tokenResponse.getEntity()).thenReturn(successCreateTokenResponse());
            when(paymentIntentsResponse.getEntity()).thenReturn(successCreatePaymentIntentsResponse());

            GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
            ChargeEntity charge = buildTestCharge(gatewayAccount);
            GatewayResponse<BaseAuthoriseResponse> response = handler.authoriseApplePay(buildApplePayAuthorisationRequest(charge));

            verify(gatewayClient, times(2)).postRequestFor(stripePostRequestCaptor.capture());
            List<StripePostRequest> allRequests = stripePostRequestCaptor.getAllValues();

            var paymentIntentRequest = (StripePaymentIntentRequest) allRequests.get(1);
            assertThat(paymentIntentRequest.getTokenId(), is("tok_1NrjK3Hj08j2jFuBm3LGVHYe"));
            assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
            assertTrue(response.getBaseResponse().isPresent());
            assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_1FHESeEZsufgnuO08A2FUSPy"));
            assertThat(response.getBaseResponse().get().getCardExpiryDate().isPresent(), is(true));
            assertThat(response.getBaseResponse().get().getCardExpiryDate().get().getTwoDigitMonth(), is("08"));
            assertThat(response.getBaseResponse().get().getCardExpiryDate().get().getTwoDigitYear(), is("24"));
        }

        @Test
        void shouldMarkChargeAsAuthorisationRejected_whenStripeRespondsWithErrorTypeCardError_forApplePay() throws Exception {
            when(gatewayClient.postRequestFor(any(StripeTokenRequest.class))).thenReturn(tokenResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse("card_error"), 400));
            when(tokenResponse.getEntity()).thenReturn(successCreateTokenResponse());

            GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
            ChargeEntity charge = buildTestCharge(gatewayAccount);
            GatewayResponse<BaseAuthoriseResponse> response = handler.authoriseApplePay(buildApplePayAuthorisationRequest(charge));

            BaseAuthoriseResponse baseAuthoriseResponse = response.getBaseResponse().get();

            verify(gatewayClient, times(2)).postRequestFor(stripePostRequestCaptor.capture());
            List<StripePostRequest> allRequests = stripePostRequestCaptor.getAllValues();

            var paymentIntentRequest = (StripePaymentIntentRequest) allRequests.get(1);
            assertThat(paymentIntentRequest.getTokenId(), is("tok_1NrjK3Hj08j2jFuBm3LGVHYe"));

            assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_REJECTED));
            assertThat(baseAuthoriseResponse.getTransactionId(), equalTo("pi_aaaaaaaaaaaaaaaaaaaaaaaa"));
            assertThat(baseAuthoriseResponse.toString(), containsString("type: card_error"));
            assertThat(baseAuthoriseResponse.toString(), containsString("message: No such charge: ch_123456 or something similar"));
            assertThat(baseAuthoriseResponse.toString(), containsString("code: resource_missing"));
        }

        @Test
        void shouldMarkChargeAsAuthorisationError_whenStripeRespondsWithErrorCreatingToken_forApplePay() throws Exception {
            when(gatewayClient.postRequestFor(any(StripeTokenRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse("api_error"), 400));

            GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
            ChargeEntity charge = buildTestCharge(gatewayAccount);
            GatewayResponse<BaseAuthoriseResponse> response = handler.authoriseApplePay(buildApplePayAuthorisationRequest(charge));

            BaseAuthoriseResponse baseAuthoriseResponse = response.getBaseResponse().get();
            assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_ERROR));
            assertThat(baseAuthoriseResponse.toString(), containsString("type: api_error"));
        }

        @Test
        void shouldMarkChargeAsAuthorisationError_whenStripeRespondsWithErrorCreatingPaymentIntent_forApplePay() throws Exception {
            when(gatewayClient.postRequestFor(any(StripeTokenRequest.class))).thenReturn(tokenResponse);
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                    .thenThrow(new GatewayErrorException("server error", errorResponse("api_error"), 400));
            when(tokenResponse.getEntity()).thenReturn(successCreateTokenResponse());

            GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
            ChargeEntity charge = buildTestCharge(gatewayAccount);
            GatewayResponse<BaseAuthoriseResponse> response = handler.authoriseApplePay(buildApplePayAuthorisationRequest(charge));

            BaseAuthoriseResponse baseAuthoriseResponse = response.getBaseResponse().get();
            assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_ERROR));
            assertThat(baseAuthoriseResponse.toString(), containsString("type: api_error"));
        }

        @Test
        void shouldAuthoriseGooglePayPayment() throws Exception {
            when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);
            when(paymentIntentsResponse.getEntity()).thenReturn(successCreatePaymentIntentsResponse());

            GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
            ChargeEntity charge = buildTestCharge(gatewayAccount);
            GatewayResponse<BaseAuthoriseResponse> response = handler.authoriseGooglePay(buildGooglePayAuthorisationRequest(charge));

            verify(gatewayClient).postRequestFor(stripePostRequestCaptor.capture());
            var paymentIntentRequest = (StripePaymentIntentRequest) (stripePostRequestCaptor.getValue());

            assertThat(paymentIntentRequest.getTokenId(), is(TOKEN_ID));

            assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
            assertTrue(response.getBaseResponse().isPresent());
            assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_1FHESeEZsufgnuO08A2FUSPy"));
            assertThat(response.getBaseResponse().get().getCardExpiryDate().isPresent(), is(true));
            assertThat(response.getBaseResponse().get().getCardExpiryDate().get().getTwoDigitMonth(), is("08"));
            assertThat(response.getBaseResponse().get().getCardExpiryDate().get().getTwoDigitYear(), is("24"));
        }
    }

    @Test
    void shouldSetAs3DSRequired_whenPaymentIntentReturnsWithRequiresAction_forGooglePay() throws Exception {
        when(paymentIntentsResponse.getEntity()).thenReturn(requires3DSCreatePaymentIntentsResponse());
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);

        GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
        gatewayAccount.setIntegrationVersion3ds(2);
        ChargeEntity charge = buildTestCharge(gatewayAccount);
        GatewayResponse<BaseAuthoriseResponse> response = handler.authoriseGooglePay(buildGooglePayAuthorisationRequest(charge));

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));
        assertTrue(response.getBaseResponse().isPresent());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_123"));

        Optional<Stripe3dsRequiredParams> stripeParamsFor3ds = (Optional<Stripe3dsRequiredParams>) response.getBaseResponse().get().getGatewayParamsFor3ds();
        assertThat(stripeParamsFor3ds.isPresent(), is(true));
        assertThat(stripeParamsFor3ds.get().toAuth3dsRequiredEntity().getIssuerUrl(), containsString("https://hooks.stripe.com"));
    }

    @Test
    void shouldMarkChargeAsAuthorisationRejected_whenStripeRespondsWithErrorTypeCardError_forGooglePay() throws Exception {
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                .thenThrow(new GatewayErrorException("server error", errorResponse("card_error"), 400));

        GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
        ChargeEntity charge = buildTestCharge(gatewayAccount);
        GatewayResponse<BaseAuthoriseResponse> response = handler.authoriseGooglePay(buildGooglePayAuthorisationRequest(charge));

        BaseAuthoriseResponse baseAuthoriseResponse = response.getBaseResponse().get();

        assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_REJECTED));
        assertThat(baseAuthoriseResponse.getTransactionId(), equalTo("pi_aaaaaaaaaaaaaaaaaaaaaaaa"));
        assertThat(baseAuthoriseResponse.toString(), containsString("type: card_error"));
        assertThat(baseAuthoriseResponse.toString(), containsString("message: No such charge: ch_123456 or something similar"));
        assertThat(baseAuthoriseResponse.toString(), containsString("code: resource_missing"));
    }

    @Test
    void shouldMarkChargeAsAuthorisationError_whenStripeRespondsWithErrorCreatingPaymentIntent_forGooglePay() throws Exception {
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                .thenThrow(new GatewayErrorException("server error", errorResponse("api_error"), 400));

        GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
        ChargeEntity charge = buildTestCharge(gatewayAccount);
        GatewayResponse<BaseAuthoriseResponse> response = handler.authoriseGooglePay(buildGooglePayAuthorisationRequest(charge));

        BaseAuthoriseResponse baseAuthoriseResponse = response.getBaseResponse().get();
        assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_ERROR));
        assertThat(baseAuthoriseResponse.toString(), containsString("type: api_error"));
    }

    private String successCreateCustomerResponse() {
        return load(STRIPE_CUSTOMER_SUCCESS_RESPONSE);
    }

    private String successCreatePaymentMethodResponse() {
        return load(STRIPE_PAYMENT_METHOD_SUCCESS_RESPONSE);
    }

    private String successCreatePaymentIntentsResponse() {
        return load(STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE);
    }

    private String successCreatePaymentIntentResponseWithCustomer() {
        return load(STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE_WITH_CUSTOMER);
    }

    private String requires3DSCreatePaymentIntentsResponse() {
        return load(STRIPE_PAYMENT_INTENT_REQUIRES_3DS_RESPONSE);
    }

    private String successCreateTokenResponse() {
        return load(STRIPE_TOKEN_SUCCESS_RESPONSE);
    }

    private String errorResponse(String... errorType) {
        String type = (errorType == null || errorType.length == 0)
                ? "invalid_request_error"
                : errorType[0];

        String code = (errorType == null || errorType.length <= 1)
                ? "resource_missing"
                : errorType[1];
        return load(STRIPE_ERROR_RESPONSE).replace("{{type}}", type).replace("{{code}}", code);
    }

    private ChargeEntity buildTestCharge() {
        return buildTestCharge(buildTestGatewayAccountEntity());
    }

    private ChargeEntity buildTestCharge(GatewayAccountEntity accountEntity) {
        return aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withGatewayAccountEntity(accountEntity)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .build();
    }

    private ChargeEntity buildTestChargeToSetUpAgreement(GatewayAccountEntity accountEntity, String agreementDescription) {
        return aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withGatewayAccountEntity(accountEntity)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .withAgreementEntity(anAgreementEntity().withDescription(agreementDescription).build())
                .withSavePaymentInstrumentToAgreement(true)
                .build();
    }

    private ChargeEntity buildTestAuthorisationModeAgreementCharge() {
        PaymentInstrumentEntity paymentInstrumentEntity = aPaymentInstrumentEntity()
                .withStripeRecurringAuthToken(CUSTOMER_ID, PAYMENT_METHOD_ID)
                .build();
        return aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withGatewayAccountEntity(buildTestGatewayAccountEntity())
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .withAgreementEntity(anAgreementEntity().build())
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withPaymentInstrument(paymentInstrumentEntity)
                .build();
    }

    private CardAuthorisationGatewayRequest buildTestAuthorisationRequest(ChargeEntity chargeEntity) {
        return new CardAuthorisationGatewayRequest(chargeEntity, buildTestAuthCardDetails());
    }

    private CardAuthorisationGatewayRequest buildTestUsAuthorisationRequest(ChargeEntity chargeEntity) {
        return new CardAuthorisationGatewayRequest(chargeEntity, buildTestUsAuthCardDetails());
    }

    private AuthCardDetails buildTestAuthCardDetails() {
        Address address = new Address("10", "Wxx", "E1 8xx", "London", null, "GB");
        return AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("Mr. Payment")
                .withCardNo("4242424242424242")
                .withCvc("111")
                .withEndDate(CardExpiryDate.valueOf("08/99"))
                .withCardBrand("visa")
                .withAddress(address)
                .build();
    }

    private AuthCardDetails buildTestUsAuthCardDetails() {
        Address address = new Address("10", "Wxx", "90210", "Washington D.C.", null, "US");
        return AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder(CARD_HOLDER)
                .withCardNo("4242424242424242")
                .withCvc("111")
                .withEndDate(CardExpiryDate.valueOf("08/99"))
                .withCardBrand("visa")
                .withAddress(address)
                .build();
    }

    private GatewayAccountEntity buildTestGatewayAccountEntity() {
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

    private ApplePayAuthorisationGatewayRequest buildApplePayAuthorisationRequest(ChargeEntity chargeEntity) {
        ApplePayPaymentInfo applePayPaymentInfo = anApplePayPaymentInfo()
                .withLastDigitsCardNumber("4242")
                .withBrand("visa")
                .withCardType(PayersCardType.DEBIT)
                .withCardholderName("Mr. Payment")
                .withEmail("foo@example.com")
                .build();
        ApplePayAuthRequest applePayAuthRequest = new ApplePayAuthRequest(
                applePayPaymentInfo, "***ENCRYPTED_PAYMENT_DATA***");

        return new ApplePayAuthorisationGatewayRequest(chargeEntity, applePayAuthRequest);
    }

    private GooglePayAuthorisationGatewayRequest buildGooglePayAuthorisationRequest(ChargeEntity chargeEntity) {
        GooglePayAuthRequest googlePayAuthRequest = new GooglePayAuthRequest(
                aGooglePayPaymentInfo().build(), TOKEN_ID
        );

        return new GooglePayAuthorisationGatewayRequest(chargeEntity, googlePayAuthRequest);
    }
}
