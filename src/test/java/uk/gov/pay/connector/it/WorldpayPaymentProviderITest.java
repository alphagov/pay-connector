package uk.gov.pay.connector.it;

import org.junit.Test;
import uk.gov.pay.connector.model.Amount;
import uk.gov.pay.connector.model.Browser;
import uk.gov.pay.connector.model.Card;
import uk.gov.pay.connector.model.CardAuthorisationRequest;
import uk.gov.pay.connector.model.CardAuthorisationResponse;
import uk.gov.pay.connector.model.GatewayAccount;
import uk.gov.pay.connector.model.Session;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.ws.rs.client.ClientBuilder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.Card.aCard;


public class WorldpayPaymentProviderITest {

    @Test
    public void shouldSendSuccessfullyAOrderForMerchant() throws Exception {

        WorldpayPaymentProvider connector = new WorldpayPaymentProvider(ClientBuilder.newClient());
        CardAuthorisationRequest request = getCardAuthorisationRequest();
        CardAuthorisationResponse response = connector.authorise(new GatewayAccount(), request);

        assertTrue(response.isSuccessful());
    }

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() throws Exception {

        WorldpayPaymentProvider connector = new WorldpayPaymentProvider(ClientBuilder.newClient());
        CardAuthorisationRequest request = getCardAuthorisationRequest();
        CardAuthorisationResponse response = connector.authorise(new GatewayAccount("wrongUsername", "wrongPassword"), request);

        assertFalse(response.isSuccessful());
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
