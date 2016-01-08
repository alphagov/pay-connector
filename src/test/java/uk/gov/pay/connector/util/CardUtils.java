package uk.gov.pay.connector.util;

import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Card;

import static uk.gov.pay.connector.model.domain.Card.aCard;

public class CardUtils {

    public static Card aValidCard() {
        String validSandboxCard = "4242424242424242";
        return buildCardDetails(validSandboxCard, "123", "12/17");
    }

    public static Card buildCardDetails(String cardNumber, String cvc, String expiryDate) {
        return buildCardDetails("Mr. Payment", cardNumber, cvc, expiryDate, goodAddress());
    }

    public static Card buildCardDetails(String cardholderName, String cardNumber, String cvc, String expiryDate, Address address) {
        Card cardDetails = aCard();
        cardDetails.setCvc(cvc);
        cardDetails.setCardHolder(cardholderName);
        cardDetails.setEndDate(expiryDate);
        cardDetails.setCardNo(cardNumber);
        cardDetails.setAddress(address);
        return cardDetails;
    }

    public static Address goodAddress() {
        return addressFor("The Money Pool", "London", "DO11 4RS", "GB");
    }

    public static Address addressFor(String line1, String city, String postcode, String country) {
        Address address = Address.anAddress();
        address.setLine1(line1);
        address.setLine2(line1);
        address.setCity(city);
        address.setPostcode(postcode);
        address.setCountry(country);
        return address;
    }
}
