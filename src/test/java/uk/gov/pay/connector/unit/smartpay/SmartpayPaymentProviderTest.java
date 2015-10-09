package uk.gov.pay.connector.unit.smartpay;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.Address.anAddress;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;

public class SmartpayPaymentProviderTest {
    private Client client;
    private SmartpayPaymentProvider provider;

    private String pcpReference = "12345678";

    @Before
    public void setup() throws Exception {
        client = mock(Client.class);
        mockSmartpaySuccessfulOrderSubmitResponse();
        provider = new SmartpayPaymentProvider(new GatewayClient(client, "http://smartpay.url"), gatewayAccountFor("theUsername", "thePassword"));
    }

    @Test
    public void shouldSendSuccessfullyAOrderForMerchant() throws Exception {
        AuthorisationResponse response = provider.authorise(getCardAuthorisationRequest());
        assertTrue(response.isSuccessful());
        assertThat(response.getTransactionId(), is(notNullValue()));
    }

    @Test
    public void shouldCaptureAPaymentSuccessfully() throws Exception {
        mockSmartpaySuccessfulCaptureResponse();
        CaptureResponse response = provider.capture(new CaptureRequest("5000", "transaction-id"));
        assertTrue(response.isSuccessful());
    }

    private AuthorisationRequest getCardAuthorisationRequest() {
        Card card = getValidTestCard();
        String amount = "222";

        String description = "This is the description";
        return new AuthorisationRequest("chargeId", card, amount, description);
    }

    private void mockSmartpaySuccessfulOrderSubmitResponse() {
        mockSmartpayResponse(200, successAuthoriseResponse());
    }

    private void mockSmartpaySuccessfulCaptureResponse() {
        mockSmartpayResponse(200, successCaptureResponse());
    }

    private void mockSmartpayResponse(int httpStatus, String responsePayload) {
        WebTarget mockTarget = mock(WebTarget.class);
        when(client.target(anyString())).thenReturn(mockTarget);
        Invocation.Builder mockBuilder = mock(Invocation.Builder.class);
        when(mockTarget.request(APPLICATION_XML)).thenReturn(mockBuilder);
        when(mockBuilder.header(anyString(), anyObject())).thenReturn(mockBuilder);

        Response response = mock(Response.class);
        when(response.readEntity(String.class)).thenReturn(responsePayload);
        when(mockBuilder.post(any(Entity.class))).thenReturn(response);

        when(response.getStatus()).thenReturn(httpStatus);
    }

    private String successAuthoriseResponse() {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
                "               xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "    <soap:Body>\n" +
                "        <ns1:authoriseResponse xmlns:ns1=\"http://payment.services.adyen.com\">\n" +
                "            <ns1:paymentResult>\n" +
                "                <additionalData xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <authCode xmlns=\"http://payment.services.adyen.com\">87802</authCode>\n" +
                "                <dccAmount xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <dccSignature xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <fraudResult xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <issuerUrl xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <md xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <paRequest xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <pspReference xmlns=\"http://payment.services.adyen.com\">" + pcpReference + "</pspReference>\n" +
                "                <refusalReason xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <resultCode xmlns=\"http://payment.services.adyen.com\">Authorised</resultCode>\n" +
                "            </ns1:paymentResult>\n" +
                "        </ns1:authoriseResponse>\n" +
                "    </soap:Body>\n" +
                "</soap:Envelope>";
    }

    private String successCaptureResponse() {
        return "<ns0:Envelope xmlns:ns0=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ns1=\"http://payment.services.adyen.com\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "    <ns0:Body>\n" +
                "        <ns1:captureResponse>\n" +
                "            <ns1:captureResult>\n" +
                "                <ns1:additionalData xsi:nil=\"true\" />\n" +
                "                <ns1:pspReference>8614440510830227</ns1:pspReference>\n" +
                "                <ns1:response>[capture-received]</ns1:response>\n" +
                "            </ns1:captureResult>\n" +
                "        </ns1:captureResponse>\n" +
                "    </ns0:Body>\n" +
                "</ns0:Envelope>";
    }

    private Card getValidTestCard() {
        Address address = anAddress();
        address.setLine1("123 My Street");
        address.setLine2("This road");
        address.setPostcode("SW8URR");
        address.setCity("London");
        address.setCounty("London state");
        address.setCountry("GB");

        return buildCardDetails("Mr. Payment", "4111111111111111", "123", "12/15", address);
    }
}