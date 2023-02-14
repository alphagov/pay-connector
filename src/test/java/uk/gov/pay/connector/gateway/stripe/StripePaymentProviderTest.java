package uk.gov.pay.connector.gateway.stripe;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.request.StripeCustomerRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentMethodRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePostRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferInRequest;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsRequiredParams;
import uk.gov.pay.connector.gateway.stripe.response.StripeDisputeData;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.queue.tasks.dispute.BalanceTransaction;
import uk.gov.pay.connector.queue.tasks.dispute.EvidenceDetails;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.Auth3dsRequiredEntityFixture.anAuth3dsRequiredEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CUSTOMER_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_REQUIRES_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE_WITH_CUSTOMER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_METHOD_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_TRANSFER_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class StripePaymentProviderTest {
    
    private static final String CARD_HOLDER = "Mr. Payment";

    @Captor
    private ArgumentCaptor<StripeTransferInRequest> stripeTransferInRequestCaptor;
    
    @Captor
    private ArgumentCaptor<StripePostRequest> stripePostRequestCaptor;
    
    private StripePaymentProvider provider;
    private final GatewayClient gatewayClient = mock(GatewayClient.class);
    private final GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
    private final Environment environment = mock(Environment.class);
    private final MetricRegistry metricRegistry = mock(MetricRegistry.class);
    private final ConnectorConfiguration configuration = mock(ConnectorConfiguration.class);
    private final StripeGatewayConfig gatewayConfig = mock(StripeGatewayConfig.class);
    private final LinksConfig linksConfig = mock(LinksConfig.class);
    private final JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());
    private final GatewayClient.Response customerResponse = mock(GatewayClient.Response.class);
    private final GatewayClient.Response paymentMethodResponse = mock(GatewayClient.Response.class);
    private final GatewayClient.Response paymentIntentsResponse = mock(GatewayClient.Response.class);

    private static final String issuerUrl = "http://stripe.url/3ds";
    private static final String threeDsVersion = "2.0.1";

    @BeforeEach
    void before() {
        when(gatewayConfig.getUrl()).thenReturn("http://stripe.url");
        when(gatewayConfig.getAuthTokens()).thenReturn(mock(StripeAuthTokens.class));
        when(configuration.getStripeConfig()).thenReturn(gatewayConfig);

        when(configuration.getLinks()).thenReturn(linksConfig);
        when(linksConfig.getFrontendUrl()).thenReturn("http://frontendUrl");

        when(gatewayClientFactory.createGatewayClient(eq(STRIPE), any(MetricRegistry.class))).thenReturn(gatewayClient);

        when(environment.metrics()).thenReturn(metricRegistry);

        provider = new StripePaymentProvider(gatewayClientFactory, configuration, objectMapper, environment);

        when(paymentMethodResponse.getEntity()).thenReturn(successCreatePaymentMethodResponse());
        when(paymentIntentsResponse.getEntity()).thenReturn(successCreatePaymentIntentsResponse());
    }

    @Test
    void shouldGetPaymentProviderName() {
        assertThat(provider.getPaymentGatewayName().getName(), is("stripe"));
    }

    @Test
    void shouldGenerateNoTransactionId() {
        assertThat(provider.generateTransactionId().isPresent(), is(false));
    }

    @Test
    void shouldAuthoriseImmediately_whenPaymentIntentReturnsAsRequiresCapture() throws Exception {
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);

        GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
        gatewayAccount.setIntegrationVersion3ds(2);
        ChargeEntity charge = buildTestCharge(gatewayAccount);
        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest(charge), charge);

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_1FHESeEZsufgnuO08A2FUSPy"));
    }

    @Test
    void shouldAuthorise_ForAddressInUs() throws Exception {
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);

        GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
        gatewayAccount.setIntegrationVersion3ds(2);
        ChargeEntity charge = buildTestCharge(gatewayAccount);
        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestUsAuthorisationRequest(charge), charge);

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_1FHESeEZsufgnuO08A2FUSPy"));
    }

    @Test
    void shouldAuthoriseSetUpRecurringPaymentAgreement() throws Exception {
        final var agreementDescription = "an agreement description";
        
        when(gatewayClient.postRequestFor(any(StripeCustomerRequest.class))).thenReturn(customerResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);
        when(customerResponse.getEntity()).thenReturn(successCreateCustomerResponse());
        when(paymentIntentsResponse.getEntity()).thenReturn(successCreatePaymentIntentResponseWithCustomer());

        GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
        ChargeEntity charge = buildTestChargeWithAgreement(gatewayAccount, agreementDescription);
        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest(charge), charge);

        verify(gatewayClient, times(3)).postRequestFor(stripePostRequestCaptor.capture());
        var customerRequest = (StripeCustomerRequest)(stripePostRequestCaptor.getAllValues().get(1));
        var paymentIntentRequest = (StripePaymentIntentRequest)(stripePostRequestCaptor.getAllValues().get(2));

        assertThat(customerRequest.getName(), is(CARD_HOLDER));
        assertThat(customerRequest.getDescription(), is(agreementDescription));
        assertThat(paymentIntentRequest.getCustomerId(), is("cus_4QFOF3xrvBT3nU"));
        assertThat(response.getBaseResponse().get().getGatewayRecurringAuthToken().isPresent(), is(true));
        assertThat(response.getBaseResponse().get().getGatewayRecurringAuthToken().get().get(STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY), is("cus_4QFOF3xrvBT3nU"));
        assertThat(response.getBaseResponse().get().getGatewayRecurringAuthToken().get().get(STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY), is("pm_1FHESdEZsufgnuO0bzghQoZ2"));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_1FHESeEZsufgnuO08A2FUSPy"));
    }

    @Test
    void shouldSetAs3DSRequired_whenPaymentIntentReturnsWithRequiresAction() throws Exception {
        when(paymentIntentsResponse.getEntity()).thenReturn(requires3DSCreatePaymentIntentsResponse());
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);

        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);

        GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
        gatewayAccount.setIntegrationVersion3ds(2);
        ChargeEntity charge = buildTestCharge(gatewayAccount);
        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest(charge), charge);

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_123")); // id from templates/stripe/create_payment_intent_requires_3ds_response.json

        Optional<Stripe3dsRequiredParams> stripeParamsFor3ds = (Optional<Stripe3dsRequiredParams>) response.getBaseResponse().get().getGatewayParamsFor3ds();
        assertThat(stripeParamsFor3ds.isPresent(), is(true));
        assertThat(stripeParamsFor3ds.get().toAuth3dsRequiredEntity().getIssuerUrl(), containsString("https://hooks.stripe.com"));
    }

    @Test
    void shouldNotAuthorise_whenProcessingExceptionIsThrown() throws Exception {

        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                .thenThrow(new GatewayConnectionTimeoutException("javax.ws.rs.ProcessingException: java.io.IOException"));
        
        ChargeEntity charge = buildTestCharge();
        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest(charge), charge);

        assertThat(authoriseResponse.isFailed(), is(true));
        assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
        assertEquals("javax.ws.rs.ProcessingException: java.io.IOException",
                authoriseResponse.getGatewayError().get().getMessage());
        assertEquals(GATEWAY_CONNECTION_TIMEOUT_ERROR, authoriseResponse.getGatewayError().get().getErrorType());
    }

    @Test
    void shouldMarkChargeAsAuthorisationRejected_whenStripeRespondsWithErrorTypeCardError() throws Exception {
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                .thenThrow(new GatewayErrorException("server error", errorResponse("card_error"), 400));
        
        ChargeEntity charge = buildTestCharge();
        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest(charge), charge);
        assertThat(authoriseResponse.getBaseResponse().isPresent(), is(true));

        BaseAuthoriseResponse baseAuthoriseResponse = authoriseResponse.getBaseResponse().get();
        assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_REJECTED));
        assertThat(baseAuthoriseResponse.getTransactionId(), equalTo("pi_aaaaaaaaaaaaaaaaaaaaaaaa"));
        assertThat(baseAuthoriseResponse.toString(), containsString("type: card_error"));
        assertThat(baseAuthoriseResponse.toString(), containsString("message: No such charge: ch_123456 or something similar"));
        assertThat(baseAuthoriseResponse.toString(), containsString("code: resource_missing"));
    }

    @Test
    void shouldMarkChargeAsAuthorisationError_whenStripeRespondsWithErrorTypeOtherThanCardError() throws Exception {
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                .thenThrow(new GatewayErrorException("server error", errorResponse("api_error"), 400));
        
        ChargeEntity charge = buildTestCharge();
        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest(charge), charge);
        assertThat(authoriseResponse.getBaseResponse().isPresent(), is(true));

        BaseAuthoriseResponse baseAuthoriseResponse = authoriseResponse.getBaseResponse().get();
        assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_ERROR));
        assertThat(baseAuthoriseResponse.toString(), containsString("type: api_error"));
    }

    @Test
    void shouldThrow_IfTryingToAuthoriseAnApplePayPayment() {
        assertThrows(UnsupportedOperationException.class, () -> provider.authoriseWallet(null));
    }

    @Test
    void shouldNotAuthorise_whenPaymentProviderReturnsUnexpectedStatusCodeFromCreatePaymentMethod() throws Exception {
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class)))
                .thenThrow(new GatewayErrorException("server error", errorResponse(), 500));

        ChargeEntity charge = buildTestCharge();
        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest(charge), charge);

        assertThat(authoriseResponse.isFailed(), is(true));
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

        ChargeEntity charge = buildTestCharge();
        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest(charge), charge);

        assertThat(authoriseResponse.isFailed(), is(true));
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

        ChargeEntity charge = buildTestChargeWithAgreement(buildTestGatewayAccountEntity(), "agreement description");
        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest(charge), charge);

        assertThat(authoriseResponse.isFailed(), is(true));
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
        when(customerResponse.getEntity()).thenReturn(successCreateCustomerResponse());

        ChargeEntity charge = buildTestChargeWithAgreement(buildTestGatewayAccountEntity(), "agreement description");
        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest(charge), charge);

        assertThat(authoriseResponse.isFailed(), is(true));
        assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
        assertThat(authoriseResponse.getGatewayError().get().getMessage(),
                containsString("There was an internal server error"));
        assertThat(authoriseResponse.getGatewayError().get().getErrorType(), is(GATEWAY_ERROR));
    }

    @Test
    void shouldMark3DSChargeAsSuccess_when3DSAuthDetailsStatusIsAuthorised() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome.AUTHORISED);
        
        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getMappedChargeStatus(), is(AUTHORISATION_SUCCESS));
        assert3dsRequiredEntityForResponse(response);
    }

    @Test
    void shouldReject3DSCharge_when3DSAuthDetailsStatusIsRejected() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome.DECLINED);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getMappedChargeStatus(), is(AUTHORISATION_REJECTED));
        assert3dsRequiredEntityForResponse(response);
    }

    @Test
    void shouldCancel3DSCharge_when3DSAuthDetailsStatusIsCanceled() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome.CANCELED);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_CANCELLED));
        assert3dsRequiredEntityForResponse(response);
    }

    @Test
    void shouldMark3DSChargeAsError_when3DSAuthDetailsStatusIsError() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome.ERROR);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_ERROR));
        assert3dsRequiredEntityForResponse(response);
    }

    @Test
    void shouldKeep3DSChargeInAuthReadyState_when3DSAuthDetailsAreNotAvailable() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(null);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertTrue(response.isSuccessful());
        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_3DS_READY));
    }

    @Test
    void shouldThrowExceptionWhenMoreThanOneBalanceTransactionPresentForDispute() throws Exception {
        BalanceTransaction balanceTransaction = new BalanceTransaction(6500L, 1500L, -8000L);
        BalanceTransaction balanceTransaction2 = new BalanceTransaction(6500L, 1500L, 8000L);
        EvidenceDetails evidenceDetails = new EvidenceDetails(1642679160L);
        StripeDisputeData stripeDisputeData = new StripeDisputeData("du_1LIaq8Dv3CZEaFO2MNQJK333",
                "pi_123456789", "needs_response", 6500L, "fradulent", 1642579160L, List.of(balanceTransaction,
                balanceTransaction2), evidenceDetails, null, true);
        
        var gatewayAccountEntity = buildTestGatewayAccountEntity();
        ChargeEntity chargeEntity = buildTestCharge(gatewayAccountEntity);
        Charge charge = Charge.from(chargeEntity);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> provider.transferDisputeAmount(stripeDisputeData, charge, gatewayAccountEntity, chargeEntity.getGatewayAccountCredentialsEntity()));
        assertThat(exception.getMessage(), is("Expected lost dispute to have a single balance_transaction, but has 2"));
    }
    
    @Test
    void shouldMakeTransferRequestForLostDispute() throws Exception {
        BalanceTransaction balanceTransaction = new BalanceTransaction(-6500L, 1500L, -8000L);
        EvidenceDetails evidenceDetails = new EvidenceDetails(1642679160L);
        String stripeDisputeId = "du_1LIaq8Dv3CZEaFO2MNQJK333";
        String paymentIntentId = "pi_123456789";
        StripeDisputeData stripeDisputeData = new StripeDisputeData(stripeDisputeId,
                paymentIntentId, "needs_response", 6500L, "fradulent", 
                1642579160L, List.of(balanceTransaction), evidenceDetails, null, false);
        String disputeExternalId = RandomIdGenerator.idFromExternalId(stripeDisputeId);

        var gatewayAccountEntity = buildTestGatewayAccountEntity();
        ChargeEntity chargeEntity = buildTestCharge(gatewayAccountEntity);
        Charge charge = Charge.from(chargeEntity);

        String stripePlatformAccountId = "platform-account-id";
        when(gatewayConfig.getPlatformAccountId()).thenReturn(stripePlatformAccountId);
        
        GatewayClient.Response response = mock(GatewayClient.Response.class);
        when(response.getEntity()).thenReturn(load(STRIPE_TRANSFER_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripeTransferInRequest.class))).thenReturn(response);
        
        provider.transferDisputeAmount(stripeDisputeData, charge, gatewayAccountEntity, chargeEntity.getGatewayAccountCredentialsEntity());
        
        verify(gatewayClient).postRequestFor(stripeTransferInRequestCaptor.capture());

        String payload = stripeTransferInRequestCaptor.getValue().getGatewayOrder().getPayload();

        assertThat(payload, containsString("destination=" + stripePlatformAccountId));
        assertThat(payload, containsString("amount=8000"));
        assertThat(payload, containsString("transfer_group=" + charge.getExternalId()));
        assertThat(payload, containsString("expand%5B%5D=balance_transaction"));
        assertThat(payload, containsString("expand%5B%5D=destination_payment"));
        assertThat(payload, containsString("currency=GBP"));
        assertThat(payload, containsString("metadata%5Bstripe_charge_id%5D=" + paymentIntentId));
        assertThat(payload, containsString("metadata%5Bgovuk_pay_transaction_external_id%5D=" + disputeExternalId));
    }

    private void assert3dsRequiredEntityForResponse(Gateway3DSAuthorisationResponse response) {
        assertThat(response.getGateway3dsRequiredParams().isPresent(), is(true));
        Auth3dsRequiredEntity auth3dsRequiredEntity = response.getGateway3dsRequiredParams().get().toAuth3dsRequiredEntity();
        assertThat(auth3dsRequiredEntity.getIssuerUrl(), is(issuerUrl));
        assertThat(auth3dsRequiredEntity.getThreeDsVersion(), is(threeDsVersion));
    }

    private Auth3dsResponseGatewayRequest build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome auth3dsResultOutcome) {
        Auth3dsResult auth3dsResult = new Auth3dsResult();
        if (auth3dsResultOutcome != null) {
            auth3dsResult.setAuth3dsResult(auth3dsResultOutcome.toString());
            auth3dsResult.setThreeDsVersion(threeDsVersion);
        }
        ChargeEntity chargeEntity = build3dsRequiredTestCharge();

        return new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsResult);
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

    private String errorResponse(String... errorType) {
        String type = (errorType == null || errorType.length == 0)
                ? "invalid_request_error"
                : errorType[0];

        return load(STRIPE_ERROR_RESPONSE).replace("{{type}}", type);
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

    private ChargeEntity buildTestChargeWithAgreement(GatewayAccountEntity accountEntity, String agreementDescription) {
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
    
    private ChargeEntity build3dsRequiredTestCharge() {
        String transactionId = "pi_a-payment-intent-id";
        
        Auth3dsRequiredEntity auth3dsRequiredEntity = anAuth3dsRequiredEntity().withIssuerUrl(issuerUrl).build();
        return aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withTransactionId(transactionId)
                .withGatewayAccountEntity(buildTestGatewayAccountEntity())
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .withAuth3dsDetailsEntity(auth3dsRequiredEntity)
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
        var gatewayAccountEntity =  aGatewayAccountEntity()
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
