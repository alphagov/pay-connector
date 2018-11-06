package uk.gov.pay.connector.util;

import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static uk.gov.pay.connector.gateway.model.AuthCardDetails.anAuthCardDetails;

public class AuthUtils {

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/yy");
    private static String expiryDate = ZonedDateTime.now().plusYears(1).format(formatter);

    public static AuthCardDetails aValidAuthorisationDetails() {
        String validSandboxCard = "4242424242424242";
        return buildAuthCardDetails(validSandboxCard, "123", expiryDate, "card-brand");
    }

    public static AuthCardDetails buildAuthCardDetails(String cardHolderName) {
        String validSandboxCard = "4242424242424242";
        return buildAuthCardDetails(cardHolderName, validSandboxCard, "123", expiryDate, "card-brand", goodAddress());
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
        return new Address(line1, line2, postcode, city, county, country);
    }

    public static Auth3dsDetails buildAuth3dsDetails() {
        Auth3dsDetails auth3dsDetails = new Auth3dsDetails();
        auth3dsDetails.setPaResponse("sample-pa-response");
        return auth3dsDetails;
    }

}
