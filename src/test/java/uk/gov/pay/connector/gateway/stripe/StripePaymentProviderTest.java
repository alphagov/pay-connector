package uk.gov.pay.connector.gateway.stripe;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.events.EventService;
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
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentMethodRequest;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsRequiredParams;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.Auth3dsRequiredEntityFixture.anAuth3dsRequiredEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_REQUIRES_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_METHOD_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

public class StripePaymentProviderTest {

    private StripePaymentProvider provider;
    private GatewayClient gatewayClient = mock(GatewayClient.class);
    private GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
    private Environment environment = mock(Environment.class);
    private MetricRegistry metricRegistry = mock(MetricRegistry.class);
    private ConnectorConfiguration configuration = mock(ConnectorConfiguration.class);
    private StripeGatewayConfig gatewayConfig = mock(StripeGatewayConfig.class);
    private LinksConfig linksConfig = mock(LinksConfig.class);
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());
    private GatewayClient.Response paymentMethodResponse = mock(GatewayClient.Response.class);
    private GatewayClient.Response paymentIntentsResponse = mock(GatewayClient.Response.class);
    private Clock clock = mock(Clock.class);
    private EventService eventService = mock(EventService.class);
    
    private static final String issuerUrl = "http://stripe.url/3ds";
    private static final String threeDsVersion = "2.0.1";

    @Before
    public void before() {
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
    public void shouldGetPaymentProviderName() {
        assertThat(provider.getPaymentGatewayName().getName(), is("stripe"));
    }

    @Test
    public void shouldGenerateNoTransactionId() {
        assertThat(provider.generateTransactionId().isPresent(), is(false));
    }

    @Test
    public void shouldAuthoriseImmediately_whenPaymentIntentReturnsAsRequiresCapture() throws Exception {
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);

        GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
        gatewayAccount.setIntegrationVersion3ds(2);
        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest(gatewayAccount));

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_1FHESeEZsufgnuO08A2FUSPy"));
    }

    @Test
    public void shouldAuthorise_ForAddressInUs() throws Exception {
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);

        GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
        gatewayAccount.setIntegrationVersion3ds(2);
        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestUsAuthorisationRequest(gatewayAccount));

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_1FHESeEZsufgnuO08A2FUSPy"));
    }

    @Test
    public void shouldSetAs3DSRequired_whenPaymentIntentReturnsWithRequiresAction() throws Exception {
        when(paymentIntentsResponse.getEntity()).thenReturn(requires3DSCreatePaymentIntentsResponse());
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);

        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class))).thenReturn(paymentIntentsResponse);

        GatewayAccountEntity gatewayAccount = buildTestGatewayAccountEntity();
        gatewayAccount.setIntegrationVersion3ds(2);
        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest(gatewayAccount));

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("pi_123")); // id from templates/stripe/create_payment_intent_requires_3ds_response.json

        Optional<Stripe3dsRequiredParams> stripeParamsFor3ds = (Optional<Stripe3dsRequiredParams>) response.getBaseResponse().get().getGatewayParamsFor3ds();
        assertThat(stripeParamsFor3ds.isPresent(), is(true));
        assertThat(stripeParamsFor3ds.get().toAuth3dsRequiredEntity().getIssuerUrl(), containsString("https://hooks.stripe.com"));
    }

    @Test
    public void shouldNotAuthorise_whenProcessingExceptionIsThrown() throws Exception {

        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                .thenThrow(new GatewayConnectionTimeoutException("javax.ws.rs.ProcessingException: java.io.IOException"));

        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest());

        assertThat(authoriseResponse.isFailed(), is(true));
        assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
        assertEquals("javax.ws.rs.ProcessingException: java.io.IOException",
                authoriseResponse.getGatewayError().get().getMessage());
        assertEquals(GATEWAY_CONNECTION_TIMEOUT_ERROR, authoriseResponse.getGatewayError().get().getErrorType());
    }

    @Test
    public void shouldMarkChargeAsAuthorisationRejected_whenStripeRespondsWithErrorTypeCardError() throws Exception {
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                .thenThrow(new GatewayErrorException("server error", errorResponse("card_error"), 400));

        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest());
        assertThat(authoriseResponse.getBaseResponse().isPresent(), is(true));

        BaseAuthoriseResponse baseAuthoriseResponse = authoriseResponse.getBaseResponse().get();
        assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_REJECTED));
        assertThat(baseAuthoriseResponse.toString(), containsString("type: card_error"));
        assertThat(baseAuthoriseResponse.toString(), containsString("code: resource_missing"));
        assertThat(baseAuthoriseResponse.toString(), containsString("message: No such charge: ch_123456 or something similar"));
    }

    @Test
    public void shouldMarkChargeAsAuthorisationError_whenStripeRespondsWithErrorTypeOtherThanCardError() throws Exception {
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                .thenThrow(new GatewayErrorException("server error", errorResponse("api_error"), 400));

        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest());
        assertThat(authoriseResponse.getBaseResponse().isPresent(), is(true));

        BaseAuthoriseResponse baseAuthoriseResponse = authoriseResponse.getBaseResponse().get();
        assertThat(baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus(), is(AUTHORISATION_ERROR));
        assertThat(baseAuthoriseResponse.toString(), containsString("type: api_error"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldThrow_IfTryingToAuthoriseAnApplePayPayment() {
        provider.authoriseWallet(null);
    }

    @Test
    public void shouldNotAuthorise_whenPaymentProviderReturnsUnexpectedStatusCode() throws Exception {
        when(gatewayClient.postRequestFor(any(StripePaymentMethodRequest.class))).thenReturn(paymentMethodResponse);
        when(gatewayClient.postRequestFor(any(StripePaymentIntentRequest.class)))
                .thenThrow(new GatewayErrorException("server error", errorResponse(), 500));
        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest());

        assertThat(authoriseResponse.isFailed(), is(true));
        assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
        assertThat(authoriseResponse.getGatewayError().get().getMessage(),
                containsString("There was an internal server error"));
        assertThat(authoriseResponse.getGatewayError().get().getErrorType(), is(GATEWAY_ERROR));
    }

    @Test
    public void shouldMark3DSChargeAsSuccess_when3DSAuthDetailsStatusIsAuthorised() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome.AUTHORISED);
        
        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getMappedChargeStatus(), is(AUTHORISATION_SUCCESS));
        assert3dsRequiredEntityForResponse(response);
    }

    @Test
    public void shouldReject3DSCharge_when3DSAuthDetailsStatusIsRejected() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome.DECLINED);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getMappedChargeStatus(), is(AUTHORISATION_REJECTED));
        assert3dsRequiredEntityForResponse(response);
    }

    @Test
    public void shouldCancel3DSCharge_when3DSAuthDetailsStatusIsCanceled() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome.CANCELED);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_CANCELLED));
        assert3dsRequiredEntityForResponse(response);
    }

    @Test
    public void shouldMark3DSChargeAsError_when3DSAuthDetailsStatusIsError() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsResult.Auth3dsResultOutcome.ERROR);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_ERROR));
        assert3dsRequiredEntityForResponse(response);
    }

    @Test
    public void shouldKeep3DSChargeInAuthReadyState_when3DSAuthDetailsAreNotAvailable() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(null);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertTrue(response.isSuccessful());
        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_3DS_READY));
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

    private String successCreatePaymentMethodResponse() {
        return load(STRIPE_PAYMENT_METHOD_SUCCESS_RESPONSE);
    }

    private String successCreatePaymentIntentsResponse() {
        return load(STRIPE_PAYMENT_INTENT_SUCCESS_RESPONSE);
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

    private CardAuthorisationGatewayRequest buildTestAuthorisationRequest() {
        return buildTestAuthorisationRequest(buildTestGatewayAccountEntity());
    }


    private CardAuthorisationGatewayRequest buildTestAuthorisationRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withGatewayAccountEntity(accountEntity)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .build();
        return buildTestAuthorisationRequest(chargeEntity);
    }

    private CardAuthorisationGatewayRequest buildTestUsAuthorisationRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withGatewayAccountEntity(accountEntity)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .build();
        return buildTestUsAuthorisationRequest(chargeEntity);
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
                .withCardHolder("Mr. Payment")
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
