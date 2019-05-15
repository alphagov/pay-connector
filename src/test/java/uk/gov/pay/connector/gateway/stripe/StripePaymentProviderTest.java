package uk.gov.pay.connector.gateway.stripe;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.request.StripeAuthoriseRequest;
import uk.gov.pay.connector.gateway.stripe.response.StripeParamsFor3ds;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.JsonObjectMapper;

import javax.ws.rs.WebApplicationException;
import java.net.URI;
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
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_3DS_SOURCES_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_SOURCES_3DS_REQUIRED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_SOURCES_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_CREATE_TOKEN_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(JUnitParamsRunner.class)
public class StripePaymentProviderTest {
    
    private StripePaymentProvider provider;
    private GatewayClient gatewayClient = mock(GatewayClient.class);
    private GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
    private Environment environment = mock(Environment.class);
    private MetricRegistry metricRegistry = mock(MetricRegistry.class);
    private ConnectorConfiguration configuration = mock(ConnectorConfiguration.class);
    private StripeGatewayConfig gatewayConfig = mock(StripeGatewayConfig.class);
    private LinksConfig linksConfig = mock(LinksConfig.class);
    private URI tokensUrl;
    private URI sourcesUrl;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());
    private GatewayClient.Response tokenResponse = mock(GatewayClient.Response.class);
    private GatewayClient.Response chargeResponse = mock(GatewayClient.Response.class);
    private GatewayClient.Response sourceResponse = mock(GatewayClient.Response.class);

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
        tokensUrl = URI.create(gatewayConfig.getUrl() + "/v1/tokens");
        sourcesUrl = URI.create(gatewayConfig.getUrl() + "/v1/sources");

        when(tokenResponse.getEntity()).thenReturn(successTokenResponse());
        when(chargeResponse.getEntity()).thenReturn(successChargeResponse());
        when(sourceResponse.getEntity()).thenReturn(successSourceResponse());
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
    @Parameters({"recommended", "required"})
    public void shouldAuthoriseAs3dsRequired_whenStripeSourceSupports3ds(String threeDSecureOption) throws Exception {

        when(gatewayClient.postRequestFor(eq(tokensUrl), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(tokenResponse);
        
        GatewayClient.Response sourceResponseWith3dsRequired = mock(GatewayClient.Response.class);
        when(sourceResponseWith3dsRequired.getEntity()).thenReturn(successSourceResponseWith3dsRequired(threeDSecureOption));
        GatewayClient.Response threeDsSourceResponse = mock(GatewayClient.Response.class);
        when(threeDsSourceResponse.getEntity()).thenReturn(success3dsSourceResponse());
        when(gatewayClient.postRequestFor(eq(sourcesUrl), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class)))
                .thenReturn(sourceResponseWith3dsRequired, threeDsSourceResponse);

        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest());

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("src_1DXAxYC6H5MjhE5Y4jZVJwNV")); // id from templates/stripe/create_3ds_sources_response.json

        Optional<StripeParamsFor3ds> stripeParamsFor3ds = (Optional<StripeParamsFor3ds>) response.getBaseResponse().get().getGatewayParamsFor3ds();
        assertThat(stripeParamsFor3ds.isPresent(), is(true));
        assertThat(stripeParamsFor3ds.get().toAuth3dsDetailsEntity().getIssuerUrl(), containsString("https://hooks.stripe.com")); //from templates/stripe/create_3ds_sources_response.json
    }

    /**
     * See https://stripe.com/docs/sources/three-d-secure
     */
    @Test
    @Parameters({"optional", "not_supported"})
    public void shouldAuthoriseWithout3DSRequired_whenStripeAdvisesNotNeeded(String threeDSecureOption) throws Exception {

        when(gatewayClient.postRequestFor(eq(tokensUrl), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(tokenResponse);

        GatewayClient.Response sourceResponseWith3dsRequired = mock(GatewayClient.Response.class);
        when(sourceResponseWith3dsRequired.getEntity()).thenReturn(successSourceResponseWith3dsRequired(threeDSecureOption));
        GatewayClient.Response threeDsSourceResponse = mock(GatewayClient.Response.class);
        when(threeDsSourceResponse.getEntity()).thenReturn(success3dsSourceResponse());
        when(gatewayClient.postRequestFor(eq(sourcesUrl), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class)))
                .thenReturn(sourceResponseWith3dsRequired, threeDsSourceResponse);
        when(gatewayClient.postRequestFor(any(StripeAuthoriseRequest.class))).thenReturn(chargeResponse);

        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest());

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
        assertThat(response.getBaseResponse().get().getTransactionId(), is("ch_1DRQ842eZvKYlo2CPbf7NNDv_test")); // id from templates/stripe/authorisation_success_response.json

        Optional<StripeParamsFor3ds> stripeParamsFor3ds = (Optional<StripeParamsFor3ds>) response.getBaseResponse().get().getGatewayParamsFor3ds();
        assertThat(stripeParamsFor3ds.isPresent(), is(false));
    }

    @Test
    public void shouldAuthoriseChargeImmediately_whenStripe3dsSourceIsChargeable() throws Exception {

        when(gatewayClient.postRequestFor(eq(tokensUrl), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(tokenResponse);

        GatewayClient.Response sourceResponseWith3dsRequired = mock(GatewayClient.Response.class);
        when(sourceResponseWith3dsRequired.getEntity()).thenReturn(successSourceResponseWith3dsRequired("recommended"));
        GatewayClient.Response threeDsSourceResponse = mock(GatewayClient.Response.class);
        when(threeDsSourceResponse.getEntity()).thenReturn(success3dsSourceResponse("chargeable"));
        when(gatewayClient.postRequestFor(eq(sourcesUrl), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class)))
                .thenReturn(sourceResponseWith3dsRequired, threeDsSourceResponse);

        when(gatewayClient.postRequestFor(any(StripeAuthoriseRequest.class))).thenReturn(chargeResponse);

        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest());

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("ch_1DRQ842eZvKYlo2CPbf7NNDv_test")); 
    }

    @Test
    public void shouldNotAuthorise_whenProcessingExceptionIsThrown() throws Exception {
        
        when(gatewayClient.postRequestFor(eq(tokensUrl), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(tokenResponse);
        when(gatewayClient.postRequestFor(eq(sourcesUrl), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(sourceResponse);

        when(gatewayClient.postRequestFor(any(StripeAuthoriseRequest.class)))
                .thenThrow(new GatewayConnectionTimeoutException("javax.ws.rs.ProcessingException: java.io.IOException"));

        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest());

        assertThat(authoriseResponse.isFailed(), is(true));
        assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
        assertEquals("javax.ws.rs.ProcessingException: java.io.IOException",
                authoriseResponse.getGatewayError().get().getMessage());
        assertEquals(GATEWAY_CONNECTION_TIMEOUT_ERROR, authoriseResponse.getGatewayError().get().getErrorType());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldThrow_IfTryingToAuthoriseAnApplePayPayment() {
        provider.authoriseWallet(null);
    }

    @Test
    public void shouldNotAuthorise_whenPaymentProviderReturnsUnexpectedStatusCode() throws Exception {
        when(gatewayClient.postRequestFor(eq(tokensUrl), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(tokenResponse);
        when(gatewayClient.postRequestFor(eq(sourcesUrl), any(GatewayAccountEntity.class), any(GatewayOrder.class), any(Map.class))).thenReturn(sourceResponse);
        when(gatewayClient.postRequestFor(any(StripeAuthoriseRequest.class)))
                .thenThrow(new GatewayErrorException("server error", errorResponse(), 500));
        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest());

        assertThat(authoriseResponse.isFailed(), is(true));
        assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
        assertThat(authoriseResponse.getGatewayError().get().getMessage(),
                containsString("There was an internal server error"));
        assertThat(authoriseResponse.getGatewayError().get().getErrorType(), is(GATEWAY_ERROR));
    }

    @Test
    public void shouldAuthorise3DSSource_when3DSAuthDetailsStatusIsAuthorised() throws Exception {
        when(gatewayClient.postRequestFor(any(StripeAuthoriseRequest.class))).thenReturn(chargeResponse);

        Auth3dsResponseGatewayRequest request = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertTrue(response.isSuccessful());
        assertThat(response.getTransactionId().get(), is("ch_1DRQ842eZvKYlo2CPbf7NNDv_test"));
        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_SUCCESS));
    }

    @Test
    public void shouldReject3DSCharge_when3DSAuthDetailsStatusIsRejected() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.DECLINED);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertTrue(response.isDeclined());
        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_REJECTED));
    }

    @Test
    public void shouldCancel3DSCharge_when3DSAuthDetailsStatusIsCanceled() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.CANCELED);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_CANCELLED));
    }

    @Test
    public void shouldMark3DSChargeAsError_when3DSAuthDetailsStatusIsError() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.ERROR);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_ERROR));
    }

    @Test
    public void shouldMark3DSChargeAsError_whenGatewayOperationResultedInUnauthorisedException() throws Exception {
        Auth3dsResponseGatewayRequest request = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

        when(gatewayClient.postRequestFor(any(StripeAuthoriseRequest.class)))
                .thenThrow(new GatewayErrorException("Unexpected HTTP status code 401 from gateway", "", HttpStatus.SC_UNAUTHORIZED));
        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_ERROR));
    }

    @Test
    public void shouldMark3DSChargeAsRejected_whenGatewayOperationResultedIn4xxHttpStatus() throws Exception {
        Auth3dsResponseGatewayRequest request = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

        when(gatewayClient.postRequestFor(any(StripeAuthoriseRequest.class)))
                .thenThrow(new GatewayErrorException("Unexpected HTTP status code 403 from gateway", errorResponse(), HttpStatus.SC_FORBIDDEN));
        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_REJECTED));
    }

    @Test
    public void shouldMark3DSChargeAsError_whenGatewayOperationResultedInException() throws Exception {
        Auth3dsResponseGatewayRequest request = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

        when(gatewayClient.postRequestFor(any(StripeAuthoriseRequest.class)))
                .thenThrow(new GatewayErrorException("server error", errorResponse(), 501));

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertTrue(response.isException());
        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_ERROR));
    }

    @Test
    public void shouldKeep3DSChargeInAuthReadyState_when3DSAuthDetailsAreNotAvailable() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(null);

        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertTrue(response.isSuccessful());
        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_3DS_READY));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionFor3DSCharge_IfStripeAccountIsNotAvailable() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

        request.getGatewayAccount().setCredentials(ImmutableMap.of());
        provider.authorise3dsResponse(request);
    }

    private Auth3dsResponseGatewayRequest build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult auth3dsResult) {
        Auth3dsDetails auth3dsDetails = new Auth3dsDetails();
        if (auth3dsResult != null) {
            auth3dsDetails.setAuth3dsResult(auth3dsResult.toString());
        }
        ChargeEntity chargeEntity = buildTestCharge();

        return new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsDetails);
    }

    private String successTokenResponse() {
        return load(STRIPE_CREATE_TOKEN_SUCCESS_RESPONSE);
    }

    private String successSourceResponseWith3dsRequired(String threeDSecureOption) {
        return load(STRIPE_CREATE_SOURCES_3DS_REQUIRED_RESPONSE).replace("{{three_d_secure_option}}", threeDSecureOption);
    }

    private String success3dsSourceResponse() {
        return success3dsSourceResponse("pending");
    }

    private String success3dsSourceResponse(String threeDSourceStatus) {
        return load(STRIPE_CREATE_3DS_SOURCES_RESPONSE)
                .replace("{{three_d_source_status}}", threeDSourceStatus);
    }

    private String successChargeResponse() {
        return load(STRIPE_AUTHORISATION_SUCCESS_RESPONSE);
    }

    private String successSourceResponse() {
        return load(STRIPE_CREATE_SOURCES_SUCCESS_RESPONSE);
    }

    private String errorResponse() {
        return load(STRIPE_ERROR_RESPONSE);
    }

    private CardAuthorisationGatewayRequest buildTestAuthorisationRequest() {
        return buildTestAuthorisationRequest(buildTestGatewayAccountEntity());
    }

    private CardAuthorisationGatewayRequest buildTestAuthorisationRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withGatewayAccountEntity(accountEntity)
                .build();
        return buildTestAuthorisationRequest(chargeEntity);
    }

    private ChargeEntity buildTestCharge() {
        ChargeEntity mq4ht90j2oir6am585afk58kml = aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withTransactionId("transaction-id")
                .withGatewayAccountEntity(buildTestGatewayAccountEntity())
                .build();
        return mq4ht90j2oir6am585afk58kml;
    }

    private CardAuthorisationGatewayRequest buildTestAuthorisationRequest(ChargeEntity chargeEntity) {
        return new CardAuthorisationGatewayRequest(chargeEntity, buildTestAuthCardDetails());
    }

    private AuthCardDetails buildTestAuthCardDetails() {
        Address address = new Address("10", "Wxx", "E1 8xx", "London", null, "GB");
        return AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("Mr. Payment")
                .withCardNo("4242424242424242")
                .withCvc("111")
                .withEndDate("08/99")
                .withCardBrand("visa")
                .withAddress(address)
                .build();
    }

    private GatewayAccountEntity buildTestGatewayAccountEntity() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("stripe");
        gatewayAccount.setRequires3ds(false);
        gatewayAccount.setCredentials(ImmutableMap.of("stripe_account_id", "stripe_account_id"));
        gatewayAccount.setType(TEST);
        return gatewayAccount;
    }
}
