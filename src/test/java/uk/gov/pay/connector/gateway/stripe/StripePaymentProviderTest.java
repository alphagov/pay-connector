package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeCreateChargeResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeSourcesResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeTokenResponse;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsSourceResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeParamsFor3ds;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;

@RunWith(MockitoJUnitRunner.class)
public class StripePaymentProviderTest extends BaseStripePaymentProviderTest {

    @Test
    public void shouldGetPaymentProviderName() {
        assertThat(provider.getPaymentGatewayName().getName(), is("stripe"));
    }

    @Test
    public void shouldGenerateNoTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(false));
    }

    @Test
    public void shouldAuthoriseAs3dsRequired_whenChargeRequired3ds() throws IOException {
        mockPaymentProvider3dsRequiredResponse();

        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(buildTestAuthorisationRequest());

        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS));
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("src_1DXAxYC6H5MjhE5Y4jZVJwNV")); // id from templates/stripe/create_3ds_sources_response.json

        Optional<StripeParamsFor3ds> stripeParamsFor3ds = (Optional<StripeParamsFor3ds>) response.getBaseResponse().get().getGatewayParamsFor3ds();
        assertThat(stripeParamsFor3ds.isPresent(), is(true));
        assertThat(stripeParamsFor3ds.get().toAuth3dsDetailsEntity().getIssuerUrl(), containsString("https://hooks.stripe.com")); //from templates/stripe/create_3ds_sources_response.json

    }

    @Test
    public void shouldNotAuthorise_whenProcessingExceptionIsThrown() {
        mockProcessingException();

        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest());

        assertThat(authoriseResponse.isFailed(), is(true));
        assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
        assertEquals(authoriseResponse.getGatewayError().get(), new GatewayError("javax.ws.rs.ProcessingException",
                GENERIC_GATEWAY_ERROR));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldThrow_IfTryingToAuthoriseAnApplePayPayment() {
        provider.authoriseApplePay(null);
    }

    @Test
    public void shouldNotAuthorise_whenPaymentProviderReturnsUnexpectedStatusCode() {
        mockResponseWithPayload(500);

        GatewayResponse<BaseAuthoriseResponse> authoriseResponse = provider.authorise(buildTestAuthorisationRequest());

        assertThat(authoriseResponse.isFailed(), is(true));
        assertThat(authoriseResponse.getGatewayError().isPresent(), is(true));
        assertThat(authoriseResponse.getGatewayError().get().getMessage(),
                containsString("There was an internal server error"));
        assertThat(authoriseResponse.getGatewayError().get().getErrorType(), is(UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));

    }

    @Test
    public void shouldAuthorise3DSSource_when3DSAuthDetailsStatusIsAuthorised() throws IOException {
        mock3dsChargeSuccessResponse();
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

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
    public void shouldMark3DSChargeAsError_whenGatewayOperationResultedInUnauthorisedException() {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

        mockResponseWithPayload(401);
        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_ERROR));
    }

    @Test
    public void shouldMark3DSChargeAsRejected_whenGatewayOperationResultedIn4xxHttpStatus() throws IOException {
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

        mockPaymentProviderErrorResponse(403, errorResponse());
        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);

        assertThat(response.getMappedChargeStatus(), is(ChargeStatus.AUTHORISATION_REJECTED));
    }

    @Test
    public void shouldMark3DSChargeAsError_whenGatewayOperationResultedInException() {
        mockProcessingException();
        Auth3dsResponseGatewayRequest request
                = build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult.AUTHORISED);

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
        Gateway3DSAuthorisationResponse response = provider.authorise3dsResponse(request);
    }

    private void mockProcessingException() {
        mockInvocationBuilder();
        when(mockClientInvocationBuilder.post(any())).thenThrow(ProcessingException.class);
    }

    private void mockPaymentProvider3dsRequiredResponse() throws IOException {
        Response response = mockResponseWithPayload(200);

        StripeTokenResponse stripeTokenResponse = new ObjectMapper().readValue(successTokenResponse(), StripeTokenResponse.class);
        when(response.readEntity(StripeTokenResponse.class)).thenReturn(stripeTokenResponse);

        StripeSourcesResponse stripeSourcesResponse = new ObjectMapper().readValue(successSourceResponseWith3dsRequired(), StripeSourcesResponse.class);
        when(response.readEntity(StripeSourcesResponse.class)).thenReturn(stripeSourcesResponse);

        Stripe3dsSourceResponse stripe3dsSourceResponse = new ObjectMapper().readValue(success3dsSourceResponse(), Stripe3dsSourceResponse.class);
        when(response.readEntity(Stripe3dsSourceResponse.class)).thenReturn(stripe3dsSourceResponse);
    }

    private Auth3dsResponseGatewayRequest build3dsResponseGatewayRequest(Auth3dsDetails.Auth3dsResult auth3dsResult) {
        Auth3dsDetails auth3dsDetails = new Auth3dsDetails();
        if (auth3dsResult != null) {
            auth3dsDetails.setAuth3dsResult(auth3dsResult.toString());
        }
        ChargeEntity chargeEntity = buildTestCharge();
        Auth3dsResponseGatewayRequest request = new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsDetails);

        return request;
    }

    private void mock3dsChargeSuccessResponse() throws IOException {
        Response response = mockResponseWithPayload(200);
        StripeCreateChargeResponse stripeCreateChargeResponse = new ObjectMapper().readValue(successChargeResponse(), StripeCreateChargeResponse.class);
        when(response.readEntity(StripeCreateChargeResponse.class)).thenReturn(stripeCreateChargeResponse);
    }
}
