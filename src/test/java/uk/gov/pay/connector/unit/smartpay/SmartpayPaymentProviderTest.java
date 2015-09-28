package uk.gov.pay.connector.unit.smartpay;


import org.junit.Test;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.GatewayErrorType.GenericGatewayError;
import static uk.gov.pay.connector.model.domain.Address.anAddress;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;


public class SmartpayPaymentProviderTest {

    private final Client client = mock(Client.class);
    private final SmartpayPaymentProvider connector = new SmartpayPaymentProvider(client, gatewayAccountFor("theUsername", "thePassword"), "http://smartpay.url");

    private String pcpReference = "12345678";
    @Test
    public void shouldSendSuccessfullyAOrderForMerchant() throws Exception {
        mockWorldpaySuccessfulOrderSubmitResponse();

        AuthorisationResponse response = connector.authorise(getCardAuthorisationRequest());
        assertTrue(response.isSuccessful());
        assertThat(response.getTransactionId(), is(pcpReference));
    }

    private AuthorisationRequest getCardAuthorisationRequest() {
        Card card = getValidTestCard();
        String amount =  "222";

        String description = "This is the description";
        return new AuthorisationRequest(card, amount, description);
    }

    private void mockWorldpaySuccessfulOrderSubmitResponse() {
        mockWorldpayResponse(200, successAuthoriseResponse());
    }

    private void mockWorldpayResponse(int httpStatus, String responsePayload) {
        WebTarget mockTarget = mock(WebTarget.class);
        when(client.target(anyString())).thenReturn(mockTarget);
        Invocation.Builder mockBuilder = mock(Invocation.Builder.class);
        when(mockTarget.request(MediaType.APPLICATION_XML)).thenReturn(mockBuilder);
        when(mockBuilder.header(anyString(), anyObject())).thenReturn(mockBuilder);
        Response response = mock(Response.class);

        when(response.readEntity(String.class)).thenReturn(responsePayload);
        when(response.getStatus()).thenReturn(httpStatus);
        when(mockBuilder.post(any(Entity.class))).thenReturn(response);
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
                "                <pspReference xmlns=\"http://payment.services.adyen.com\">"+pcpReference+"</pspReference>\n" +
                "                <refusalReason xmlns=\"http://payment.services.adyen.com\" xsi:nil=\"true\"/>\n" +
                "                <resultCode xmlns=\"http://payment.services.adyen.com\">Authorised</resultCode>\n" +
                "            </ns1:paymentResult>\n" +
                "        </ns1:authoriseResponse>\n" +
                "    </soap:Body>\n" +
                "</soap:Envelope>";
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