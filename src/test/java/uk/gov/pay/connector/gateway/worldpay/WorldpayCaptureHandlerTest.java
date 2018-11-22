package uk.gov.pay.connector.gateway.worldpay;

import com.google.common.collect.ImmutableMap;
import fj.data.Either;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.ws.rs.core.Response;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;
import static uk.gov.pay.connector.gateway.model.GatewayError.unexpectedStatusCodeFromGateway;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayCaptureHandlerTest {

    private WorldpayCaptureHandler worldpayCaptureHandler;

    @Mock
    private GatewayClient client;
    @Mock
    private Response response;

    @Before
    public void setup() {
        worldpayCaptureHandler = new WorldpayCaptureHandler(client);
    }

    @Test
    public void shouldCaptureAPaymentSuccessfully() throws Exception {
        //mock client.postRequestFor
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        Either<GatewayError, GatewayClient.Response> response = right(new TestResponse(this.response));
        when(client.postRequestFor(isNull(), any(GatewayAccountEntity.class), any(GatewayOrder.class))).thenReturn(response);

        //mock client.unmarshallResponse
        Either<GatewayError, WorldpayCaptureResponse> unmarshalledResponse = right(XMLUnmarshaller.unmarshall(successCaptureResponse(), WorldpayCaptureResponse.class));
        when(client.unmarshallResponse(any(TestResponse.class), any(Class.class))).thenReturn(unmarshalledResponse);

        GatewayResponse gatewayResponse = worldpayCaptureHandler.capture(getCaptureRequest());
        assertTrue(gatewayResponse.isSuccessful());
    }

    private class TestResponse extends GatewayClient.Response {

        protected TestResponse(Response delegate) {
            super(delegate);
        }
    }

    @Test
    public void shouldErrorIfOrderReferenceNotKnownInCapture() throws Exception {
        //mock client.postRequestFor
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        Either<GatewayError, GatewayClient.Response> response = right(new TestResponse(this.response));
        when(client.postRequestFor(isNull(), any(GatewayAccountEntity.class), any(GatewayOrder.class))).thenReturn(response);

        //mock client.unmarshallResponse
        Either<GatewayError, WorldpayCaptureResponse> unmarshalledResponse = right(XMLUnmarshaller.unmarshall(errorResponse(), WorldpayCaptureResponse.class));
        when(client.unmarshallResponse(any(TestResponse.class), any(Class.class))).thenReturn(unmarshalledResponse);

        GatewayResponse<BaseCaptureResponse> gatewayResponse = worldpayCaptureHandler.capture(getCaptureRequest());

        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), is("Worldpay capture response (error code: 5, error: Order has already been paid)"));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldErrorIfWorldpayResponseIsNot200() {
        //mock client.postRequestFor
        Either<GatewayError, GatewayClient.Response> response = left(unexpectedStatusCodeFromGateway("Unexpected HTTP status code 400 from gateway"));
        when(client.postRequestFor(isNull(), any(GatewayAccountEntity.class), any(GatewayOrder.class))).thenReturn(response);
        
        GatewayResponse<BaseCaptureResponse> gatewayResponse = worldpayCaptureHandler.capture(getCaptureRequest());
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), is("Unexpected HTTP status code 400 from gateway"));
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), is(UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
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

    private String errorResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                "    <reply>\n" +
                "        <error code=\"5\">\n" +
                "            <![CDATA[Order has already been paid]]>\n" +
                "        </error>\n" +
                "    </reply>\n" +
                "</paymentService>";
    }

    private GatewayAccountEntity aServiceAccount() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("worldpay");
        gatewayAccount.setRequires3ds(false);
        gatewayAccount.setCredentials(ImmutableMap.of(
                CREDENTIALS_MERCHANT_ID, "worlpay-merchant",
                CREDENTIALS_USERNAME, "worldpay-password",
                CREDENTIALS_PASSWORD, "password"
        ));
        gatewayAccount.setType(TEST);
        return gatewayAccount;
    }
}

