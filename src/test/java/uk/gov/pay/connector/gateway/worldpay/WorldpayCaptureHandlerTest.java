package uk.gov.pay.connector.gateway.worldpay;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayCaptureHandlerTest extends WorldpayBasePaymentProviderTest {

    private WorldpayCaptureHandler worldpayCaptureHandler;

    @Before
    public void setup() {
        super.setup();
        worldpayCaptureHandler = (WorldpayCaptureHandler) provider.getCaptureHandler();
    }

    @Test
    public void shouldCaptureAPaymentSuccessfully() throws Exception {
        mockWorldpaySuccessfulCaptureResponse();

        GatewayResponse response = worldpayCaptureHandler.capture(getCaptureRequest());
        assertTrue(response.isSuccessful());
    }

    @Test
    public void shouldErrorIfOrderReferenceNotKnownInCapture() {
        mockWorldpayErrorResponse(200);
        GatewayResponse<BaseCaptureResponse> response = worldpayCaptureHandler.capture(getCaptureRequest());

        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Worldpay capture response (error code: 5, error: Order has already been paid)", GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldErrorIfWorldpayResponseIsNot200() {
        mockWorldpayErrorResponse(400);
        GatewayResponse<BaseCaptureResponse> response = worldpayCaptureHandler.capture(getCaptureRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Unexpected HTTP status code 400 from gateway",
                UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
    }

    private void mockWorldpaySuccessfulCaptureResponse() {
        mockWorldpayResponse(200, successCaptureResponse());
    }

    private CaptureGatewayRequest getCaptureRequest() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .build();
        return CaptureGatewayRequest.valueOf(chargeEntity);
    }

    private String successCaptureResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                "    <reply>\n" +
                "        <ok>\n" +
                "            <captureReceived orderCode=\"MyUniqueTransactionId!\">\n" +
                "                <amount value=\"500\" currencyCode=\"GBP\" exponent=\"2\" debitCreditIndicator=\"credit\"/>\n" +
                "            </captureReceived>\n" +
                "        </ok>\n" +
                "    </reply>\n" +
                "</paymentService>";
    }

}

