package uk.gov.pay.connector.unit.worldpay;


import org.junit.Test;
import uk.gov.pay.connector.model.Address;
import uk.gov.pay.connector.model.Amount;
import uk.gov.pay.connector.model.Browser;
import uk.gov.pay.connector.model.Card;
import uk.gov.pay.connector.model.CardAuthorisationRequest;
import uk.gov.pay.connector.model.CardAuthorisationResponse;
import uk.gov.pay.connector.model.GatewayAccount;
import uk.gov.pay.connector.model.Session;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.Address.anAddress;
import static uk.gov.pay.connector.model.Card.aCard;



public class WorldpayPaymentProviderTest {

    private Client client = mock(Client.class);

    @Test
    public void shouldSendSuccessfullyAOrderForMerchant() throws Exception {
        mockWorldpaySuccessfulOrderSubmitResponse();

        WorldpayPaymentProvider connector = new WorldpayPaymentProvider(client, new GatewayAccount("MERCHANTCODE","password"));
        CardAuthorisationRequest request = getCardAuthorisationRequest();
        CardAuthorisationResponse response = connector.authorise(request);

        assertTrue(response.isSuccessful());
    }

    private CardAuthorisationRequest getCardAuthorisationRequest() {
        String userAgentHeader = "Mozilla/5.0 (Windows; U; Windows NT 5.1;en-GB; rv:1.9.1.5) Gecko/20091102 Firefox/3.5.5 (.NET CLR 3.5.30729)";
        String acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

        Card card = getValidTestCard();
        Session session = new Session("123.123.123.123", "0215ui8ib1");
        Browser browser = new Browser(acceptHeader, userAgentHeader);
        Amount amount = new Amount("500");

        String transactionId = "MyUniqueTransactionId!";
        String description = "This is mandatory";
        return new CardAuthorisationRequest(card, session, browser, amount, transactionId, description);
    }

    private void mockWorldpaySuccessfulOrderSubmitResponse() {
        WebTarget mockTarget = mock(WebTarget.class);
        when(client.target(anyString())).thenReturn(mockTarget);
        Invocation.Builder mockBuilder = mock(Invocation.Builder.class);
        when(mockTarget.request(MediaType.APPLICATION_XML)).thenReturn(mockBuilder);
        when(mockBuilder.header(anyString(), anyObject())).thenReturn(mockBuilder);
        Response response = mock(Response.class);

        when(response.readEntity(String.class)).thenReturn(successResponse());
        when(response.getStatus()).thenReturn(200);
        when(mockBuilder.post(any(Entity.class))).thenReturn(response);
    }

    private String successResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                "    <reply>\n" +
                "        <orderStatus orderCode=\"MyUniqueTransactionId!22233\">\n" +
                "            <payment>\n" +
                "                <paymentMethod>VISA-SSL</paymentMethod>\n" +
                "                <paymentMethodDetail>\n" +
                "                    <card number=\"4444********1111\" type=\"creditcard\">\n" +
                "                        <expiryDate>\n" +
                "                            <date month=\"11\" year=\"2099\"/>\n" +
                "                        </expiryDate>\n" +
                "                    </card>\n" +
                "                </paymentMethodDetail>\n" +
                "                <amount value=\"500\" currencyCode=\"GBP\" exponent=\"2\" debitCreditIndicator=\"credit\"/>\n" +
                "                <lastEvent>AUTHORISED</lastEvent>\n" +
                "                <AuthorisationId id=\"666\"/>\n" +
                "                <CVCResultCode description=\"NOT SENT TO ACQUIRER\"/>\n" +
                "                <AVSResultCode description=\"NOT SENT TO ACQUIRER\"/>\n" +
                "                <cardHolderName>\n" +
                "                    <![CDATA[Coucou]]>\n" +
                "                </cardHolderName>\n" +
                "                <issuerCountryCode>N/A</issuerCountryCode>\n" +
                "                <balance accountType=\"IN_PROCESS_AUTHORISED\">\n" +
                "                    <amount value=\"500\" currencyCode=\"GBP\" exponent=\"2\" debitCreditIndicator=\"credit\"/>\n" +
                "                </balance>\n" +
                "                <riskScore value=\"51\"/>\n" +
                "            </payment>\n" +
                "        </orderStatus>\n" +
                "    </reply>\n" +
                "</paymentService>";
    }

    private Card getValidTestCard() {
        Address address = anAddress();
        address.withLine1("123 My Street")
                .withLine2("This road")
                .withZip("SW8URR")
                .withCity("London")
                .withCounty("London state")
                .withCountry("GB");

        return aCard()
                .withCardDetails("Mr. Payment", "4111111111111111", "123", "12/15")
                .withAddress(address);
    }
}