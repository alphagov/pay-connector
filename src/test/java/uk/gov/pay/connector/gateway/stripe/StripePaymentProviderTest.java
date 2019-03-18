package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
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

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.DOWNSTREAM_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.CLIENT_ERROR;
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
    protected StripePaymentProvider provider;
    StripeGatewayClient mockGatewayClient = mock(StripeGatewayClient.class);
    ConnectorConfiguration configuration = mock(ConnectorConfiguration.class);
    StripeGatewayConfig gatewayConfig = mock(StripeGatewayConfig.class);
    LinksConfig linksConfig = mock(LinksConfig.class);
    private URI tokensUrl;
    private URI sourcesUrl;
    private URI chargesUrl;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());
    List<String> threeDSecureRequiredOptions = ImmutableList.of("required", "recommended", "optional");

    @Before
    public void before() {
        when(gatewayConfig.getUrl()).thenReturn("http://stripe.url");
        when(gatewayConfig.getAuthTokens()).thenReturn(mock(StripeAuthTokens.class));
        when(configuration.getStripeConfig()).thenReturn(gatewayConfig);

        when(configuration.getLinks()).thenReturn(linksConfig);
        when(linksConfig.getFrontendUrl()).thenReturn("http://frontendUrl");

        provider = new StripePaymentProvider(mockGatewayClient, configuration, objectMapper);
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
    public void shouldAuthoriseAs3dsRequired_whenStripeSourceSupports3ds(String threeDSecureOption) throws GatewayClientException, GatewayException, DownstreamException {

        when(mockGatewayClient.postRequest(eq(tokensUrl), anyString(), any(Map.class), any(MediaType.class), anyString())).thenReturn(successTokenResponse());
        when(mockGatewayClient.postRequest(eq(sourcesUrl), anyString(), any(Map.class), any(MediaType.class), anyString()))
                .thenReturn(successSourceResponseWith3dsRequired(threeDSecureOption), success3dsSourceResponse());

        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest());

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("src_1DXAxYC6H5MjhE5Y4jZVJwNV")); // id from templates/stripe/create_3ds_sources_response.json

        Optional<StripeParamsFor3ds> stripeParamsFor3ds = (Optional<StripeParamsFor3ds>) response.getBaseResponse().get().getGatewayParamsFor3ds();
        assertThat(stripeParamsFor3ds.isPresent(), is(true));
        assertThat(stripeParamsFor3ds.get().toAuth3dsDetailsEntity().getIssuerUrl(), containsString("https://hooks.stripe.com")); //from templates/stripe/create_3ds_sources_response.json
    }

    @Test
    public void shouldAuthoriseChargeImmediately_whenStripe3dsSourceIsChargeable() throws GatewayClientException, GatewayException, DownstreamException {

        when(mockGatewayClient.postRequest(eq(tokensUrl), anyString(), any(Map.class), any(MediaType.class), anyString())).thenReturn(successTokenResponse());
        when(mockGatewayClient.postRequest(eq(sourcesUrl), anyString(), any(Map.class), any(MediaType.class), anyString()))
                .thenReturn(successSourceResponseWith3dsRequired("recommended"), success3dsSourceResponse("chargeable"));
        when(mockGatewayClient.postRequest(eq(chargesUrl), anyString(), any(Map.class), any(MediaType.class), anyString())).thenReturn(successChargeResponse());

        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest());

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("ch_1DRQ842eZvKYlo2CPbf7NNDv_test")); 
    }

    @Test
    public void shouldNotAuthorise_whenProcessingExceptionIsThrown() throws GatewayClientException, GatewayException, DownstreamException {
        when(mockGatewayClient.postRequest(eq(tokensUrl), anyString(), any(Map.class), any(MediaType.class), anyString())).thenReturn(successTokenResponse());
        when(mockGatewayClient.postRequest(eq(sourcesUrl), anyString(), any(Map.class), any(MediaType.class), anyString())).thenReturn(successSourceResponse());
        when(mockGatewayClient.postRequest(eq(chargesUrl), anyString(), any(Map.class), any(MediaType.class), anyString()))
                .thenThrow(new GatewayException(chargesUrl.toString(), new ProcessingException(new IOException())));

        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest());

        assertThat(authoriseResponse.isFailed(), is(true));
        assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
        assertEquals("javax.ws.rs.ProcessingException: java.io.IOException",
                authoriseResponse.getGatewayError().get().getMessage());
        assertEquals(GENERIC_GATEWAY_ERROR, authoriseResponse.getGatewayError().get().getErrorType());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldThrow_IfTryingToAuthoriseAnApplePayPayment() {
        provider.authoriseWallet(null);
    }

    @Test
    public void shouldNotAuthorise_whenPaymentProviderReturnsUnexpectedStatusCode() throws GatewayClientException, GatewayException, DownstreamException {
        when(mockGatewayClient.postRequest(eq(tokensUrl), anyString(), any(Map.class), any(MediaType.class), anyString())).thenReturn(successTokenResponse());
        when(mockGatewayClient.postRequest(eq(sourcesUrl), anyString(), any(Map.class), any(MediaType.class), anyString())).thenReturn(successSourceResponse());
        when(mockGatewayClient.postRequest(eq(chargesUrl), anyString(), any(Map.class), any(MediaType.class), anyString())).thenThrow(new DownstreamException(500, errorResponse()));
        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest());

        assertThat(authoriseResponse.isFailed(), is(true));
        assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
        assertThat(authoriseResponse.getGatewayError().get().getMessage(),
                containsString("There was an internal server error"));
        assertThat(authoriseResponse.getGatewayError().get().getErrorType(), is(DOWNSTREAM_ERROR));
    }

    @Test
    public void shouldAuthorise3DSSource_when3DSAuthDetailsStatusIsAuthorised() throws GatewayClientException, GatewayException, DownstreamException {
        when(mockGatewayClient.postRequest(eq(chargesUrl), anyString(), any(Map.class), any(MediaType.class), anyString())).thenReturn(successChargeResponse());

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
    public void shouldMark3DSChargeAsError_whenGatewayOperationResultedInUnauthorisedException() throws GatewayClientException, GatewayException, DownstreamException {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

        StripeGatewayClientResponse mockedResponse = mock(StripeGatewayClientResponse.class);
        when(mockedResponse.getStatus()).thenReturn(401);
        when(mockGatewayClient.postRequest(eq(chargesUrl), anyString(), any(Map.class), any(MediaType.class), anyString()))
                .thenThrow(new GatewayClientException("Unexpected HTTP status code 401 from gateway", mockedResponse));
        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_ERROR));
    }

    @Test
    public void shouldMark3DSChargeAsRejected_whenGatewayOperationResultedIn4xxHttpStatus() throws GatewayClientException, GatewayException, DownstreamException {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

        StripeGatewayClientResponse mockedResponse = mock(StripeGatewayClientResponse.class);
        when(mockedResponse.getStatus()).thenReturn(403);
        when(mockedResponse.getPayload()).thenReturn(errorResponse());
        when(mockGatewayClient.postRequest(eq(chargesUrl), anyString(), any(Map.class), any(MediaType.class), anyString()))
                .thenThrow(new GatewayClientException("Unexpected HTTP status code 403 from gateway", mockedResponse));
        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_REJECTED));
    }

    @Test
    public void shouldMark3DSChargeAsError_whenGatewayOperationResultedInException() throws GatewayClientException, GatewayException, DownstreamException {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

        when(mockGatewayClient.postRequest(eq(chargesUrl), anyString(), any(Map.class), any(MediaType.class), anyString()))
                .thenThrow(new DownstreamException(501, errorResponse()));

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
