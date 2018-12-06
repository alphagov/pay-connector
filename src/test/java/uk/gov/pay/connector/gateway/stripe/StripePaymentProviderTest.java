package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.stripe.json.StripeSourcesResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeTokenResponse;
import uk.gov.pay.connector.gateway.stripe.response.Stripe3dsSourceResponse;
import uk.gov.pay.connector.paymentprocessor.service.PaymentProviderAuthorisationResponse;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS;

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

        PaymentProviderAuthorisationResponse response = provider.authorise(buildTestAuthorisationRequest());

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(REQUIRES_3DS));
        assertTrue(response.getTransactionId().isPresent());
        assertThat(response.getTransactionId().get(), is("src_1DXAxYC6H5MjhE5Y4jZVJwNV")); // id from templates/stripe/create_3ds_sources_response.json

        Optional<Auth3dsDetailsEntity> auth3dsDetailsEntity = response.getAuth3dsDetailsEntity();
        assertThat(auth3dsDetailsEntity.isPresent(), is(true));
        assertThat(auth3dsDetailsEntity.get().getIssuerUrl(), containsString("https://hooks.stripe.com")); //from templates/stripe/create_3ds_sources_response.json

    }

    @Test
    public void shouldNotAuthorise_whenProcessingExceptionIsThrown() {
        mockProcessingException();

        PaymentProviderAuthorisationResponse authoriseResponse = provider.authorise(buildTestAuthorisationRequest());

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

        PaymentProviderAuthorisationResponse authoriseResponse = provider.authorise(buildTestAuthorisationRequest());

        assertFalse(authoriseResponse.getAuthoriseStatus().isPresent());
        assertTrue(authoriseResponse.getGatewayError().isPresent());
        assertThat(authoriseResponse.getGatewayError().get().getMessage(),
                containsString("There was an internal server error"));
        assertThat(authoriseResponse.getGatewayError().get().getErrorType(), is(UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));

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

}
