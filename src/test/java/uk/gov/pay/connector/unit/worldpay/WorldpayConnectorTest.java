package uk.gov.pay.connector.unit.worldpay;


import org.junit.Test;
import uk.gov.pay.connector.model.Amount;
import uk.gov.pay.connector.model.Browser;
import uk.gov.pay.connector.model.Card;
import uk.gov.pay.connector.model.CardAuthorizationRequest;
import uk.gov.pay.connector.model.GatewayAccount;
import uk.gov.pay.connector.model.Session;
import uk.gov.pay.connector.worldpay.WorldpayConnector;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.Card.aCard;


public class WorldpayConnectorTest {

    private Client client = mock(Client.class);

    @Test
    public void shouldSendSuccessfullyAOrderForMerchant() throws Exception {
        mockWorldpaySuccessfulOrderSubmitResponse();

        WorldpayConnector connector = new WorldpayConnector(client);
        CardAuthorizationRequest request = getCardAuthorizationRequest();
        Response response = connector.authorize(new GatewayAccount(), request);

        assertThat(response.getStatus(), is(200));
    }

    private CardAuthorizationRequest getCardAuthorizationRequest() {
        String userAgentHeader = "Mozilla/5.0 (Windows; U; Windows NT 5.1;en-GB; rv:1.9.1.5) Gecko/20091102 Firefox/3.5.5 (.NET CLR 3.5.30729)";
        String acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

        Card card = getValidTestCard();
        Session session = new Session("123.123.123.123", "0215ui8ib1");
        Browser browser = new Browser(acceptHeader, userAgentHeader);
        Amount amount = new Amount("500");

        String transactionId = "MyUniqueTransactionId!";
        String description = "This is mandatory";
        return new CardAuthorizationRequest(card, session, browser, amount, transactionId, description);
    }

    private void mockWorldpaySuccessfulOrderSubmitResponse() {
        WebTarget mockTarget = mock(WebTarget.class);
        when(client.target(anyString())).thenReturn(mockTarget);
        Invocation.Builder mockBuilder = mock(Invocation.Builder.class);
        when(mockTarget.request(MediaType.APPLICATION_XML)).thenReturn(mockBuilder);
        when(mockBuilder.header(anyString(), anyObject())).thenReturn(mockBuilder);
        when(mockBuilder.post(any(Entity.class))).thenReturn(Response.ok().build());
    }

    private Card getValidTestCard() {
        return aCard()
                .withCardDetails("Mr. Payment", "4111111111111111", "123", "12/15")
                .withAddressLine1("123 My Street")
                .withAddressLine2("This road")
                .withAddressZip("SW8URR")
                .withAddressCity("London")
                .withAddressState("London state");
    }
}