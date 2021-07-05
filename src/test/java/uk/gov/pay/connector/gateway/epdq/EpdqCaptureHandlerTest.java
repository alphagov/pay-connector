package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableMap;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;

import javax.ws.rs.core.Response;
import java.net.URI;

import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
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
        epdqCaptureHandler = new EpdqCaptureHandler(client, emptyMap());
    }

    @Test
    public void shouldCapture() throws Exception {
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(response.readEntity(String.class)).thenReturn(load("templates/epdq/capture-success-response.xml"));
        TestResponse testResponse = new TestResponse(this.response);
        when(client.postRequestFor(any(URI.class), eq(EPDQ), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(testResponse);

        CaptureResponse gatewayResponse = epdqCaptureHandler.capture(buildTestCaptureRequest());
        assertTrue(gatewayResponse.isSuccessful());
        assertThat(gatewayResponse.state(), is(CaptureResponse.ChargeState.PENDING));
        assertThat(gatewayResponse.getTransactionId().isPresent(), is(true));
        assertThat(gatewayResponse.getTransactionId().get(), is("3014644340"));
    }

    private class TestResponse extends GatewayClient.Response {

        protected TestResponse(Response delegate) {
            super(delegate);
        }
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturnsUnexpectedStatusCode() throws Exception {
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(response.readEntity(String.class)).thenReturn(load("templates/epdq/capture-error-response.xml"));
        TestResponse testResponse = new TestResponse(this.response);
        when(client.postRequestFor(any(URI.class), eq(EPDQ), eq("test"), any(GatewayOrder.class), anyMap())).thenReturn(testResponse);

        CaptureResponse gatewayResponse = epdqCaptureHandler.capture(buildTestCaptureRequest());
        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.getError().isPresent(), is(true));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturnsNon200HttpStatusCode() throws Exception {
        when(client.postRequestFor(any(URI.class), eq(EPDQ), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        CaptureResponse gatewayResponse = epdqCaptureHandler.capture(buildTestCaptureRequest());
        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.getError().isPresent(), is(true));
        assertThat(gatewayResponse.getError().get().getMessage(), is("Unexpected HTTP status code 400 from gateway"));
        assertThat(gatewayResponse.getError().get().getErrorType(), is(GATEWAY_ERROR));
    }

    private CaptureGatewayRequest buildTestCaptureRequest() {
        return buildTestCaptureRequest(buildTestGatewayAccountEntity());
    }

    private GatewayAccountEntity buildTestGatewayAccountEntity() {
        return GatewayAccountEntityFixture.aGatewayAccountEntity()
                .withId(1L)
                .withGatewayName("epdq")
                .withRequires3ds(false)
                .withCredentials(ImmutableMap.of(
                        CREDENTIALS_MERCHANT_ID, "merchant-id",
                        CREDENTIALS_USERNAME, "username",
                        CREDENTIALS_PASSWORD, "password",
                        CREDENTIALS_SHA_IN_PASSPHRASE, "sha-passphrase"
                ))
                .withType(TEST)
                .build();
    }

    private CaptureGatewayRequest buildTestCaptureRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(accountEntity)
                .withTransactionId("payId")
                .build();
        return CaptureGatewayRequest.valueOf(chargeEntity);
    }
}
