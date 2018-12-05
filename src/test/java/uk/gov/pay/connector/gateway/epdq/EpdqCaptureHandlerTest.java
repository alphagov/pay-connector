package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableMap;
import fj.data.Either;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCaptureResponse;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.ws.rs.core.Response;

import static fj.data.Either.left;
import static fj.data.Either.right;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;
import static uk.gov.pay.connector.gateway.model.GatewayError.unexpectedStatusCodeFromGateway;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CAPTURE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class EpdqCaptureHandlerTest {

    private EpdqCaptureHandler epdqCaptureHandler;

    @Mock 
    private GatewayClient client;
    @Mock
    private Response response;

    @Before
    public void setup() {
        epdqCaptureHandler = new EpdqCaptureHandler(client);
    }

    @Test
    public void shouldCapture() throws Exception {
        //mock client.postRequestFor
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        Either<GatewayError, GatewayClient.Response> response = right(new TestResponse(this.response));
        when(client.postRequestFor(anyString(), any(GatewayAccountEntity.class), any(GatewayOrder.class))).thenReturn(response);

        //mock client.unmarshallResponse
        Either<GatewayError, EpdqCaptureResponse> unmarshalledResponse = right(XMLUnmarshaller.unmarshall(load(EPDQ_CAPTURE_SUCCESS_RESPONSE), EpdqCaptureResponse.class));
        when(client.unmarshallResponse(any(TestResponse.class), any(Class.class))).thenReturn(unmarshalledResponse);
        
        CaptureResponse gatewayResponse = epdqCaptureHandler.capture(buildTestCaptureRequest());
        assertTrue(gatewayResponse.isSuccessful());
        assertThat(gatewayResponse.getTransactionId().isPresent(), is(true));
        assertThat(gatewayResponse.getTransactionId().get(), is("3014644340"));
    }

    private class TestResponse extends GatewayClient.Response {

        protected TestResponse(Response delegate) {
            super(delegate);
        }
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturnsUnexpectedStatusCode() throws Exception{
        //mock client.postRequestFor
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        Either<GatewayError, GatewayClient.Response> response = right(new TestResponse(this.response));
        when(client.postRequestFor(anyString(), any(GatewayAccountEntity.class), any(GatewayOrder.class))).thenReturn(response);

        //mock client.unmarshallResponse
        Either<GatewayError, EpdqCaptureResponse> unmarshalledResponse = right(XMLUnmarshaller.unmarshall(load(EPDQ_CAPTURE_ERROR_RESPONSE), EpdqCaptureResponse.class));
        when(client.unmarshallResponse(any(TestResponse.class), any(Class.class))).thenReturn(unmarshalledResponse);
        
        CaptureResponse gatewayResponse = epdqCaptureHandler.capture(buildTestCaptureRequest());
        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.getError().isPresent(), is(true));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturnsNon200HttpStatusCode() {
        //mock client.postRequestFor
        Either<GatewayError, GatewayClient.Response> response = left(unexpectedStatusCodeFromGateway("Unexpected HTTP status code 400 from gateway"));
        when(client.postRequestFor(anyString(), any(GatewayAccountEntity.class), any(GatewayOrder.class))).thenReturn(response);
        
        CaptureResponse gatewayResponse = epdqCaptureHandler.capture(buildTestCaptureRequest());
        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.getError().isPresent(), is(true));
        assertThat(gatewayResponse.getError().get().getMessage(), is("Unexpected HTTP status code 400 from gateway"));
        assertThat(gatewayResponse.getError().get().getErrorType(), is(UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
    }

    private CaptureGatewayRequest buildTestCaptureRequest() {
        return buildTestCaptureRequest(buildTestGatewayAccountEntity());
    }

    private GatewayAccountEntity buildTestGatewayAccountEntity() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("epdq");
        gatewayAccount.setRequires3ds(false);
        gatewayAccount.setCredentials(ImmutableMap.of(
                CREDENTIALS_MERCHANT_ID, "merchant-id",
                CREDENTIALS_USERNAME, "username",
                CREDENTIALS_PASSWORD, "password",
                CREDENTIALS_SHA_IN_PASSPHRASE, "sha-passphrase"
        ));
        gatewayAccount.setType(TEST);
        return gatewayAccount;
    }

    private CaptureGatewayRequest buildTestCaptureRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(accountEntity)
                .withTransactionId("payId")
                .build();
        return CaptureGatewayRequest.valueOf(chargeEntity);
    }
}
