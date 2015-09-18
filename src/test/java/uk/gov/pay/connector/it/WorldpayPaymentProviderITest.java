package uk.gov.pay.connector.it;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Amount;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.ws.rs.client.ClientBuilder;

import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.Address.anAddress;
import static uk.gov.pay.connector.model.domain.Card.aCard;
import static uk.gov.pay.connector.utils.EnvironmentUtils.getWorldpayPassword;
import static uk.gov.pay.connector.utils.EnvironmentUtils.getWorldpayUser;


public class WorldpayPaymentProviderITest {

    @Before
    public void before() throws Exception {
        Assume.assumeTrue(worldPayEnvironmentInitialized());
    }

    @Test
    public void shouldSendSuccessfullyAOrderForMerchant() throws Exception {

        WorldpayPaymentProvider connector = new WorldpayPaymentProvider();
        AuthorisationRequest request = getCardAuthorisationRequest();
        AuthorisationResponse response = connector.authorise(request);

        assertTrue(response.isSuccessful());
    }

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() throws Exception {
        GatewayAccount gatewayAccount = new GatewayAccount("wrongUsername", "wrongPassword");

        WorldpayPaymentProvider connector = new WorldpayPaymentProvider(ClientBuilder.newClient(), gatewayAccount);

        AuthorisationRequest request = getCardAuthorisationRequest();
        AuthorisationResponse response = connector.authorise(request);

        assertFalse(response.isSuccessful());
    }

    private AuthorisationRequest getCardAuthorisationRequest() {
        Card card = getValidTestCard();
        Amount amount = new Amount("500");
        String transactionId = randomUUID().toString();
        String description = "This is mandatory";
        return new AuthorisationRequest(card, amount, transactionId, description);
    }

    private Card getValidTestCard() {
        Address address = anAddress();
        address.setLine1("123 My Street");
        address.setLine2("This road");
        address.setPostcode("SW8URR");
        address.setCity("London");
        address.setCountry("GB");

        Card cardDetails = withCardDetails("Mr. Payment", "4111111111111111", "123", "12/15");
        cardDetails.setAddress(address);
        return cardDetails;
    }

    private boolean worldPayEnvironmentInitialized() {
        return isNotBlank(getWorldpayUser()) && isNotBlank(getWorldpayPassword());
    }

    public Card withCardDetails(String cardHolder, String cardNo, String cvc, String endDate) {
        Card card = aCard();
        card.setCardHolder(cardHolder);
        card.setCardNo(cardNo);
        card.setCvc(cvc);
        card.setEndDate(endDate);
        return card;
    }

}
