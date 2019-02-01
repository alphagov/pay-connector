package uk.gov.pay.connector.gateway.smartpay;

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
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import javax.ws.rs.core.Response;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_CAPTURE_SUCCESS_RESPONSE;

@RunWith(MockitoJUnitRunner.class)
public class SmartpayCaptureHandlerTest {

    private SmartpayCaptureHandler smartpayCaptureHandler;

    @Mock
    private GatewayClient client;
    @Mock
    private Response response;

    @Before
    public void setup() {
        smartpayCaptureHandler = new SmartpayCaptureHandler(client);
    }

    @Test
    public void shouldCaptureAPaymentSuccessfully() throws Exception {
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(response.readEntity(String.class)).thenReturn(successCaptureResponse());
        when(client.postRequestFor(isNull(), any(GatewayAccountEntity.class), any(GatewayOrder.class)))
                .thenReturn(new TestResponse(this.response));
        
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .build();

        CaptureGatewayRequest request = CaptureGatewayRequest.valueOf(chargeEntity);
        CaptureResponse gatewayResponse = smartpayCaptureHandler.capture(request);
        assertTrue(gatewayResponse.isSuccessful());
        assertThat(gatewayResponse.state(), is(CaptureResponse.ChargeState.PENDING));
    }

    private String successCaptureResponse() {
        return TestTemplateResourceLoader.load(SMARTPAY_CAPTURE_SUCCESS_RESPONSE).replace("{{pspReference}}", "8614440510830227");
    }

    private GatewayAccountEntity aServiceAccount() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("smartpay");
        gatewayAccount.setCredentials(ImmutableMap.of(
                "username", "theUsername",
                "password", "thePassword",
                "merchant_id", "theMerchantCode"
        ));
        gatewayAccount.setType(TEST);

        return gatewayAccount;
    }
    
    private class TestResponse extends GatewayClient.Response {

        protected TestResponse(Response delegate) {
            super(delegate);
        }
    }
}
