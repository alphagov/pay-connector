package uk.gov.pay.connector.util;

import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.AuthorisationDetails;

import static uk.gov.pay.connector.model.domain.AuthorisationDetails.anAuthorisationDetails;

public class CardUtils {

    public static AuthorisationDetails aValidAuthorisationDetails() {
        String validSandboxCard = "4242424242424242";
        return buildAuthorisationDetails(validSandboxCard, "123", "12/17", "card-brand");
    }

    public static AuthorisationDetails buildAuthorisationDetails(String cardHolderName) {
        String validSandboxCard = "4242424242424242";
        return buildAuthorisationDetails(cardHolderName, validSandboxCard, "123", "12/21", "card-brand", goodAddress());
    }

    public static AuthorisationDetails buildAuthorisationDetails(String cardNumber, String cvc, String expiryDate, String cardBrand) {
        return buildAuthorisationDetails("Mr. Payment", cardNumber, cvc, expiryDate, cardBrand, goodAddress());
    }

    public static AuthorisationDetails buildAuthorisationDetails(String cardholderName, String cardNumber, String cvc, String expiryDate, String cardBrand, Address address) {
        AuthorisationDetails authorisationDetails = anAuthorisationDetails();
        authorisationDetails.setCvc(cvc);
        authorisationDetails.setCardHolder(cardholderName);
        authorisationDetails.setEndDate(expiryDate);
        authorisationDetails.setCardNo(cardNumber);
        authorisationDetails.setCardBrand(cardBrand);
        authorisationDetails.setAddress(address);
        authorisationDetails.setAcceptHeader("text/html");
        authorisationDetails.setUserAgentHeader("Mozilla/5.0");
        return authorisationDetails;
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
