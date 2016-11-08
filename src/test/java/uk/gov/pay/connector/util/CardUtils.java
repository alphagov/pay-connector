package uk.gov.pay.connector.util;

import uk.gov.pay.connector.model.domain.AddressEntity;
import uk.gov.pay.connector.model.domain.Card;

import static uk.gov.pay.connector.model.domain.Card.aCard;

public class CardUtils {

    public static Card aValidCard() {
        String validSandboxCard = "4242424242424242";
        return buildCardDetails(validSandboxCard, "123", "12/17", "card-brand");
    }

    public static Card buildCardDetails(String cardNumber, String cvc, String expiryDate, String cardBrand) {
        return buildCardDetails("Mr. Payment", cardNumber, cvc, expiryDate, cardBrand, goodAddress());
    }

    public static Card buildCardDetails(String cardholderName, String cardNumber, String cvc, String expiryDate, String cardBrand, AddressEntity addressEntity) {
        Card cardDetails = aCard();
        cardDetails.setCvc(cvc);
        cardDetails.setCardHolder(cardholderName);
        cardDetails.setEndDate(expiryDate);
        cardDetails.setCardNo(cardNumber);
        cardDetails.setCardBrand(cardBrand);
        cardDetails.setAddress(addressEntity);
        return cardDetails;
    }

    public static AddressEntity goodAddress() {
        return addressFor("The Money Pool", "London", "DO11 4RS", "GB");
    }

    public static AddressEntity addressFor(String line1, String city, String postcode, String country) {
        AddressEntity addressEntity = AddressEntity.anAddress();
        addressEntity.setLine1(line1);
        addressEntity.setLine2(line1);
        addressEntity.setCity(city);
        addressEntity.setPostcode(postcode);
        addressEntity.setCountry(country);
        return addressEntity;
    }
}
