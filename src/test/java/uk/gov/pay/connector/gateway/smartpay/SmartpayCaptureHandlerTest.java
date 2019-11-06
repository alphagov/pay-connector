package uk.gov.pay.connector.gateway.smartpay;

import com.google.common.collect.ImmutableMap;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.w3c.dom.Document;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.pay.connector.util.XPathUtils;

import javax.ws.rs.core.Response;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_CAPTURE_SUCCESS_RESPONSE;

@RunWith(JUnitParamsRunner.class)
public class SmartpayCaptureHandlerTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private SmartpayCaptureHandler smartpayCaptureHandler;

    @Mock
    private GatewayClient client;
    @Mock
    private Response response;

    @Before
    public void setup() {
        smartpayCaptureHandler = new SmartpayCaptureHandler(client, ImmutableMap.of(TEST.toString(), URI.create("http://worldpay.test")));
    }

    @Test
    @Parameters({"null", "250"})
    public void shouldCaptureAPaymentSuccessfully(@Nullable Long corporateSurchargeAmount) throws Exception {
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(response.readEntity(String.class)).thenReturn(successCaptureResponse());
        TestResponse testResponse = new TestResponse(this.response);
        when(client.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(testResponse);
        
        ChargeEntity chargeEntity = aValidChargeEntity().withGatewayAccountEntity(aServiceAccount()).build();
        if (corporateSurchargeAmount != null) {
            chargeEntity.setCorporateSurcharge(corporateSurchargeAmount);
        }
        CaptureResponse gatewayResponse = smartpayCaptureHandler.capture(CaptureGatewayRequest.valueOf(chargeEntity));
        
        assertTrue(gatewayResponse.isSuccessful());
        assertThat(gatewayResponse.state(), is(CaptureResponse.ChargeState.PENDING));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(client).postRequestFor(
                any(URI.class),
                any(GatewayAccountEntity.class),
                gatewayOrderArgumentCaptor.capture(),
                anyMap());

        Document document = XPathUtils.getDocumentXmlString(gatewayOrderArgumentCaptor.getValue().getPayload());
        XPath xPath = XPathFactory.newInstance().newXPath();
        assertThat(xPath.evaluate("/Envelope/Body/capture/modificationRequest/modificationAmount/value", document),
                is(String.valueOf(chargeEntity.getAmount() + chargeEntity.getCorporateSurcharge().orElse(0L))));
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
