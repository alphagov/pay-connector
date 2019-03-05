package uk.gov.pay.connector.gateway.stripe;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.http.HttpStatus;
import org.junit.Assert;
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
import uk.gov.pay.connector.gateway.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayErrorException.GatewayConnectionErrorException;
import uk.gov.pay.connector.gateway.GatewayErrorException.GenericGatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeParamsFor3ds;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.JsonObjectMapper;

import javax.ws.rs.WebApplicationException;
import java.net.URI;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_CONNECTION_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
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
    private GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
    private GatewayClient gatewayClient = mock(GatewayClient.class);
    private ConnectorConfiguration configuration = mock(ConnectorConfiguration.class);
    private StripeGatewayConfig gatewayConfig = mock(StripeGatewayConfig.class);
    private LinksConfig linksConfig = mock(LinksConfig.class);
    private Environment environment = mock(Environment.class);
    private URI tokensUrl;
    private URI sourcesUrl;
    private URI chargesUrl;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());

    @Before
    public void before() {
        when(gatewayConfig.getUrl()).thenReturn("http://stripe.url");
        when(gatewayConfig.getAuthTokens()).thenReturn(mock(StripeAuthTokens.class));
        when(configuration.getStripeConfig()).thenReturn(gatewayConfig);
        when(configuration.getLinks()).thenReturn(linksConfig);
        when(linksConfig.getFrontendUrl()).thenReturn("http://frontendUrl");
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(MetricRegistry.class)))
                .thenReturn(gatewayClient);

        provider = new StripePaymentProvider(gatewayClientFactory, configuration, objectMapper, environment);
        tokensUrl = URI.create(gatewayConfig.getUrl() + "/v1/tokens");
        sourcesUrl = URI.create(gatewayConfig.getUrl() + "/v1/sources");
        chargesUrl = URI.create(gatewayConfig.getUrl() + "/v1/charges");
    }

    @Test
    public void shouldGetPaymentProviderName() {
        assertThat(provider.getPaymentGatewayName().getName(), is("stripe"));
    }

    @Test
    public void shouldGenerateNoTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(false));
    }

    @Test
    @Parameters({"recommended", "required", "optional"})
    public void shouldAuthoriseAs3dsRequired_whenStripeSourceSupports3ds(String threeDSecureOption) throws Exception {
        
        CardAuthorisationGatewayRequest request = buildTestAuthorisationRequest();

        mockTokenResponseSuccess(request.getGatewayAccount());

        GatewayClient.Response sourceWith3dsRequiredResponse = mock(GatewayClient.Response.class);
        when(sourceWith3dsRequiredResponse.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(sourceWith3dsRequiredResponse.getEntity()).thenReturn(successSourceResponseWith3dsRequired(threeDSecureOption));

        GatewayClient.Response source3dsResponse = mock(GatewayClient.Response.class);
        when(source3dsResponse.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(source3dsResponse.getEntity()).thenReturn(success3dsSourceResponse());
        
        when(gatewayClient.postRequestFor(
                eq(sourcesUrl),
                eq(request.getGatewayAccount()),
                any(GatewayOrder.class),
                anyList(),
                anyMap(),
                eq("create_source")))
                .thenReturn(sourceWith3dsRequiredResponse, source3dsResponse);

        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(request);

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("src_1DXAxYC6H5MjhE5Y4jZVJwNV")); // id from templates/stripe/create_3ds_sources_response.json

        Optional<StripeParamsFor3ds> stripeParamsFor3ds = (Optional<StripeParamsFor3ds>) response.getBaseResponse().get().getGatewayParamsFor3ds();
        assertThat(stripeParamsFor3ds.isPresent(), is(true));
        assertThat(stripeParamsFor3ds.get().toAuth3dsDetailsEntity().getIssuerUrl(), containsString("https://hooks.stripe.com")); //from templates/stripe/create_3ds_sources_response.json
    }

    private void mockTokenResponseSuccess(GatewayAccountEntity gatewayAccount) throws GenericGatewayErrorException, GatewayConnectionErrorException, GatewayErrorException.GatewayConnectionTimeoutErrorException {
        GatewayClient.Response tokenResponse = mock(GatewayClient.Response.class);
        when(tokenResponse.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(tokenResponse.getEntity()).thenReturn(successTokenResponse());

        when(gatewayClient.postRequestFor(
                eq(tokensUrl), 
                eq(gatewayAccount), 
                any(GatewayOrder.class), 
                anyList(), 
                anyMap(),
                eq("create_token")))
                .thenReturn(tokenResponse);
    }

    @Test
    public void shouldAuthoriseChargeImmediately_whenStripe3dsSourceIsChargeable() throws Exception {

        CardAuthorisationGatewayRequest request = buildTestAuthorisationRequest();
        
        mockTokenResponseSuccess(request.getGatewayAccount());

        GatewayClient.Response sourceWith3dsRequiredResponse = mock(GatewayClient.Response.class);
        when(sourceWith3dsRequiredResponse.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(sourceWith3dsRequiredResponse.getEntity()).thenReturn(successSourceResponseWith3dsRequired("recommended"));

        GatewayClient.Response source3dsResponse = mock(GatewayClient.Response.class);
        when(source3dsResponse.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(source3dsResponse.getEntity()).thenReturn(success3dsSourceResponse("chargeable"));

        when(gatewayClient.postRequestFor(
                eq(sourcesUrl),
                eq(request.getGatewayAccount()),
                any(GatewayOrder.class),
                anyList(),
                anyMap(),
                eq("create_source")))
                .thenReturn(sourceWith3dsRequiredResponse, source3dsResponse);

        mockChargeResponseSuccess(request.getGatewayAccount());

        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(request);

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("ch_1DRQ842eZvKYlo2CPbf7NNDv_test")); 
    }

    private void mockChargeResponseSuccess(GatewayAccountEntity gatewayAccountEntity) throws GenericGatewayErrorException, GatewayConnectionErrorException, GatewayErrorException.GatewayConnectionTimeoutErrorException {
        GatewayClient.Response chargeResponse = mock(GatewayClient.Response.class);
        when(chargeResponse.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(chargeResponse.getEntity()).thenReturn(successChargeResponse());

        when(gatewayClient.postRequestFor(
                eq(chargesUrl), 
                eq(gatewayAccountEntity),
                any(GatewayOrder.class),
                anyList(),
                anyMap(),
                eq("create_charge")))
                .thenReturn(chargeResponse);
    }

    @Test
    public void shouldNotAuthorise_whenProcessingExceptionIsThrown() throws Exception {
        
        CardAuthorisationGatewayRequest request = buildTestAuthorisationRequest();
        
        mockTokenResponseSuccess(request.getGatewayAccount());
        mockSourcesResponseSuccess(request.getGatewayAccount());
        
        when(gatewayClient.postRequestFor(
                eq(chargesUrl), 
                eq(request.getGatewayAccount()),
                any(GatewayOrder.class),
                anyList(),
                anyMap(),
                eq("create_charge")))
                .thenThrow(new GenericGatewayErrorException("javax.ws.rs.ProcessingException: java.io.IOException"));

        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(request);

        assertThat(authoriseResponse.isFailed(), is(true));
        assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
        assertEquals("javax.ws.rs.ProcessingException: java.io.IOException",
                authoriseResponse.getGatewayError().get().getMessage());
        assertEquals(GENERIC_GATEWAY_ERROR, authoriseResponse.getGatewayError().get().getErrorType());
    }

    private void mockSourcesResponseSuccess(GatewayAccountEntity gatewayAccountEntity) throws Exception {
        GatewayClient.Response sourceResponse = mock(GatewayClient.Response.class);
        when(sourceResponse.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(sourceResponse.getEntity()).thenReturn(successSourceResponse());

        when(gatewayClient.postRequestFor(
                eq(sourcesUrl),
                eq(gatewayAccountEntity),
                any(GatewayOrder.class),
                anyList(),
                anyMap(),
                eq("create_source")))
                .thenReturn(sourceResponse);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldThrow_IfTryingToAuthoriseAnApplePayPayment() {
        provider.authoriseWallet(null);
    }

    @Test
    public void shouldNotAuthorise_whenPaymentProviderReturnsUnexpectedStatusCode() throws Exception {
        
        CardAuthorisationGatewayRequest request = buildTestAuthorisationRequest();

        mockTokenResponseSuccess(request.getGatewayAccount());
        mockSourcesResponseSuccess(request.getGatewayAccount());

        when(gatewayClient.postRequestFor(
                eq(chargesUrl),
                eq(request.getGatewayAccount()),
                any(GatewayOrder.class),
                anyList(),
                anyMap(),
                eq("create_charge")))
                .thenThrow(new GatewayConnectionErrorException("There was an internal server error", HttpStatus.SC_INTERNAL_SERVER_ERROR, errorResponse()));
        
        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(request);

        assertThat(authoriseResponse.isFailed(), is(true));
        assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
        assertThat(authoriseResponse.getGatewayError().get().getMessage(),
                containsString("There was an internal server error"));
        assertThat(authoriseResponse.getGatewayError().get().getErrorType(), is(GATEWAY_CONNECTION_ERROR));
    }

    @Test
    public void shouldAuthorise3DSSource_when3DSAuthDetailsStatusIsAuthorised() throws Exception {
        
        Auth3dsResponseGatewayRequest request = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

        mockChargeResponseSuccess(request.getGatewayAccount());
        
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

        when(gatewayClient.postRequestFor(
                eq(chargesUrl),
                eq(request.getGatewayAccount()),
                any(GatewayOrder.class),
                anyList(),
                anyMap(),
                eq("create_charge")))
                .thenThrow(new GatewayConnectionErrorException("U R Unauthorized", HttpStatus.SC_UNAUTHORIZED, ""));
        
        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_ERROR));
    }

    @Test
    public void shouldMark3DSChargeAsError_whenGatewayOperationResultedInException() throws Exception {
        Auth3dsResponseGatewayRequest request = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

        when(gatewayClient.postRequestFor(
                eq(chargesUrl),
                eq(request.getGatewayAccount()),
                any(GatewayOrder.class),
                anyList(),
                anyMap(),
                eq("create_charge")))
                .thenThrow(new GatewayConnectionErrorException("Internal server error", 501, errorResponse()));
        
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

    @Test(expected = WebApplicationException.class)
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
        return aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withTransactionId("transaction-id")
                .withGatewayAccountEntity(buildTestGatewayAccountEntity())
                .build();
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
