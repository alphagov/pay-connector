package uk.gov.pay.connector.gateway.epdq;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCaptureResponse;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;

@RunWith(MockitoJUnitRunner.class)
public class EpdqCaptureHandlerTest extends BaseEpdqPaymentProviderTest {

    protected EpdqCaptureHandler epdqCaptureHandler;

    @Before
    public void setup() {
        super.setup();
        epdqCaptureHandler = (EpdqCaptureHandler) provider.getCaptureHandler();
    }

    @Test
    public void shouldCapture() {
        mockPaymentProviderResponse(200, successCaptureResponse());
        GatewayResponse<EpdqCaptureResponse> response = epdqCaptureHandler.capture(buildTestCaptureRequest());
        verifyPaymentProviderRequest(successCaptureRequest());
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("3014644340"));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturnsUnexpectedStatusCode() {
        mockPaymentProviderResponse(200, errorCaptureResponse());
        GatewayResponse<EpdqCaptureResponse> response = epdqCaptureHandler.capture(buildTestCaptureRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturnsNon200HttpStatusCode() {
        mockPaymentProviderResponse(400, errorCaptureResponse());
        GatewayResponse<EpdqCaptureResponse> response = epdqCaptureHandler.capture(buildTestCaptureRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Unexpected HTTP status code 400 from gateway",
                UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
    }

}
