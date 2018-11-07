package uk.gov.pay.connector.gateway.sandbox;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(MockitoJUnitRunner.class)
public class SandboxCaptureHandlerTest {

    private SandboxCaptureHandler sandboxCaptureHandler;
    private SandboxPaymentProvider sandboxPaymentProvider;

    @Before
    public void setup() {
        SandboxPaymentProvider sandboxPaymentProvider = new SandboxPaymentProvider();
        sandboxCaptureHandler = (SandboxCaptureHandler) sandboxPaymentProvider.getCaptureHandler();
    }

    @Test
    public void capture_shouldSucceedWhenCapturingAnyCharge() {

        GatewayResponse<BaseCaptureResponse> gatewayResponse = sandboxCaptureHandler.capture(CaptureGatewayRequest.valueOf(ChargeEntityFixture.aValidChargeEntity().build()));

        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));

        BaseCaptureResponse captureResponse = gatewayResponse.getBaseResponse().get();
        assertThat(captureResponse.getTransactionId(), is(notNullValue()));
        assertThat(captureResponse.getErrorCode(), is(nullValue()));
        assertThat(captureResponse.getErrorMessage(), is(nullValue()));
    }

  
}
