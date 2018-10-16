package uk.gov.pay.connector.util;

import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

import static uk.gov.pay.connector.gateway.model.AuthCardDetails.anAuthCardDetails;

public class AuthUtils {

    public static AuthCardDetails aValidAuthorisationDetails() {
        String validSandboxCard = "4242424242424242";
        return buildAuthCardDetails(validSandboxCard, "123", "12/17", "card-brand");
    }

    public static AuthCardDetails buildAuthCardDetails(String cardHolderName) {
        String validSandboxCard = "4242424242424242";
        return buildAuthCardDetails(cardHolderName, validSandboxCard, "123", "12/21", "card-brand", goodAddress());
    }

    public static AuthCardDetails buildAuthCardDetails(String cardNumber, String cvc, String expiryDate, String cardBrand) {
        return buildAuthCardDetails("Mr. Payment", cardNumber, cvc, expiryDate, cardBrand, goodAddress());
    }

    public static AuthCardDetails buildAuthCardDetails(String cardholderName, String cardNumber, String cvc, String expiryDate, String cardBrand, Address address) {
        AuthCardDetails authCardDetails = anAuthCardDetails();
        authCardDetails.setCvc(cvc);
        authCardDetails.setCardHolder(cardholderName);
        authCardDetails.setEndDate(expiryDate);
        authCardDetails.setCardNo(cardNumber);
        authCardDetails.setCardBrand(cardBrand);
        authCardDetails.setAddress(address);
        authCardDetails.setAcceptHeader("text/html");
        authCardDetails.setUserAgentHeader("Mozilla/5.0");
        return authCardDetails;
    }

    public static Address goodAddress() {
        return addressFor("The Money Pool", "London", "DO11 4RS", "GB");
    }

    public static Address addressFor(String line1, String city, String postcode, String country) {
        return addressFor(line1, line1, city, postcode, null, country);
    }

    public static Address addressFor(String line1, String line2, String city, String postcode, String county, String country) {
        Address address = Address.anAddress();
        address.setLine1(line1);
        address.setLine2(line2);
        address.setCity(city);
        address.setPostcode(postcode);
        address.setCountry(country);
        address.setCounty(county);
        return address;
    }

    public static Auth3dsDetails buildAuth3dsDetails() {
        Auth3dsDetails auth3dsDetails = new Auth3dsDetails();
        auth3dsDetails.setPaResponse("sample-pa-response");

        return auth3dsDetails;
    }

}
