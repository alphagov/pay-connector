package uk.gov.pay.connector.gateway.smartpay;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_CAPTURE_SUCCESS_RESPONSE;

@RunWith(MockitoJUnitRunner.class)
public class SmartpayCaptureHandlerTest extends BaseSmartpayPaymentProviderTest {

    private SmartpayCaptureHandler smartpayCaptureHandler;

    @Before
    public void setup() {
        super.setup();
        smartpayCaptureHandler = (SmartpayCaptureHandler) provider.getCaptureHandler();
    }

    @Test
    public void shouldCaptureAPaymentSuccessfully() {

        mockSmartpayResponse(200, successCaptureResponse());

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .build();

        GatewayResponse<BaseCaptureResponse> response = smartpayCaptureHandler.capture(CaptureGatewayRequest.valueOf(chargeEntity));
        assertTrue(response.isSuccessful());
    }

    private String successCaptureResponse() {
        return TestTemplateResourceLoader.load(SMARTPAY_CAPTURE_SUCCESS_RESPONSE).replace("{{pspReference}}", "8614440510830227");
    }

}
