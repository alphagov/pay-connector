package uk.gov.pay.connector.gateway.stripe;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCaptureHandler;
import uk.gov.pay.connector.gateway.stripe.response.StripeCaptureResponse;

import javax.ws.rs.WebApplicationException;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;

@RunWith(MockitoJUnitRunner.class)
public class StripeCaptureHandlerTest extends BaseStripePaymentProviderTest {

    protected StripeCaptureHandler stripeCaptureHandler;

    @Before
    public void setup() {
        super.setup();
        stripeCaptureHandler = (StripeCaptureHandler) provider.getCaptureHandler();
    }

    @Test
    public void shouldCapture() throws IOException {
        mockPaymentProviderCaptureResponse(200, successCaptureResponse());

        GatewayResponse<StripeCaptureResponse> response = stripeCaptureHandler.capture(buildTestCaptureRequest());
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("ch_123456"));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturns4xxHttpStatusCode() throws IOException {
        mockPaymentProviderErrorResponse(400, errorCaptureResponse());
        GatewayResponse<StripeCaptureResponse> response = stripeCaptureHandler.capture(buildTestCaptureRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("No such charge: ch_123456 or something similar",
                UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
    }

    @Test(expected = WebApplicationException.class)
    public void shouldThrowException_whenPaymentProviderReturns5xxHttpStatusCode() throws IOException {
        mockPaymentProviderErrorResponse(500, errorCaptureResponse());
        GatewayResponse<StripeCaptureResponse> response = stripeCaptureHandler.capture(buildTestCaptureRequest());
    }

}
