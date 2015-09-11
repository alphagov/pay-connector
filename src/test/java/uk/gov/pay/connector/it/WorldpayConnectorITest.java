package uk.gov.pay.connector.it;

import org.junit.Ignore;
import org.junit.Test;
import uk.gov.pay.connector.model.Amount;
import uk.gov.pay.connector.model.Browser;
import uk.gov.pay.connector.model.Card;
import uk.gov.pay.connector.model.CardAuthorizationRequest;
import uk.gov.pay.connector.model.GatewayAccount;
import uk.gov.pay.connector.model.Session;
import uk.gov.pay.connector.worldpay.WorldpayConnector;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.Card.aCard;

@Ignore
public class WorldpayConnectorITest {
    @Test
    public void shouldSendSuccessfullyAOrderForMerchant() throws Exception {

        WorldpayConnector connector = new WorldpayConnector(ClientBuilder.newClient());
        CardAuthorizationRequest request = getCardAuthorizationRequest();
        Response response = connector.authorize(new GatewayAccount(), request);

        assertThat(response.getStatus(), is(200));
    }

    @Test
    public void shouldSendSuccessfullyAOrderForMerchantWrong() throws Exception {

        WorldpayConnector connector = new WorldpayConnector(ClientBuilder.newClient());
        CardAuthorizationRequest request = getCardAuthorizationRequest();
        Response response = connector.authorize(new GatewayAccount("wrongUsername", "wrongPassword"), request);

        assertThat(response.getStatus(), is(401));
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
