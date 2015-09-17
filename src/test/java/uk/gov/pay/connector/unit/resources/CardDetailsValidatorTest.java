package uk.gov.pay.connector.unit.resources;

import org.junit.Test;
import uk.gov.pay.connector.model.Address;
import uk.gov.pay.connector.model.Card;
import uk.gov.pay.connector.resources.CardDetailsValidator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CardDetailsValidatorTest {

    private String validCVC = "999";
    private String validCardNumber = "4242424242424242";
    private String validExpiryDate = "12/99";

    @Test
    public void validationSucceedForCorrectCardDetails() {
        Card cardDetails = buildCardDetails(validCardNumber, validCVC, validExpiryDate);
        assertTrue(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationSucceedFor14digitsCardNumber() {
        Card cardDetails = buildCardDetails("12345678901234", validCVC, validExpiryDate);
        assertTrue(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }


    @Test
    public void validationFailsForMissingCVC() {
        Card wrongCardDetails = buildCardDetails(validCardNumber, null, validExpiryDate);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsForMissingCardNumber() {
        Card wrongCardDetails = buildCardDetails(null, validCVC, validExpiryDate);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsForMissingExpiryDate() {
        Card wrongCardDetails = buildCardDetails(validCardNumber, validCVC, null);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsForEmptyFields() {
        Card wrongCardDetails = buildCardDetails("", "", "");
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsFor13digitsCardNumber() {
        Card cardDetails = buildCardDetails("1234567890123", validCVC, validExpiryDate);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForCardNumberWithNonDigits() {
        Card cardDetails = buildCardDetails("123456789012345A", validCVC, validExpiryDate);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForCVCwithNonDigits() {
        Card cardDetails = buildCardDetails(validCardNumber, "45A", validExpiryDate);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForCVCwithMoreThan3Digits() {
        Card cardDetails = buildCardDetails(validCardNumber, "4444", validExpiryDate);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForExpiryDateWithWrongFormat() {
        Card cardDetails = buildCardDetails(validCardNumber, validCVC, "1290");
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }


    @Test
    public void validationFailsForMissingAddress() {
        Card cardDetails = buildCardDetails(validCardNumber, validCVC, "1290", null);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }


    @Test
    public void validationFailsForMissingCityAddress() {
        Address address = addressFor("L1", null, "WJWHE", "GB");
        Card cardDetails = buildCardDetails(validCardNumber, validCVC, "1290", address);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForMissingLine1Address() {
        Address address = addressFor(null, "London", "WJWHE", "GB");
        Card cardDetails = buildCardDetails(validCardNumber, validCVC, "1290", address);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForMissingCountryAddress() {
        Address address = addressFor("L1", "London", "WJWHE", null);
        Card cardDetails = buildCardDetails(validCardNumber, validCVC, "1290", address);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForMissingPostcodeAddress() {
        Address address = addressFor("L1", "London", null, "GB");
        Card cardDetails = buildCardDetails(validCardNumber, validCVC, "1290", address);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }


    private Card buildCardDetails(String cardNumber, String cvc, String expiryDate) {
        return buildCardDetails(cardNumber, cvc, expiryDate, goodAddress());
    }

    private Card buildCardDetails(String cardNumber, String cvc, String expiryDate, Address address) {
        Card cardDetails = Card.aCard();

        cardDetails.setCvc(cvc);
        cardDetails.setEndDate(expiryDate);
        cardDetails.setCardNo(cardNumber);
        cardDetails.setAddress(address);
        return cardDetails;
    }

    private Address goodAddress() {
        return addressFor("The Money Pool", "London", "DO11 4RS", "GB");
    }

    private Address addressFor(String line1, String city, String postcode, String country) {
        Address address = Address.anAddress();
        address.setLine1(line1);
        address.setCity(city);
        address.setPostcode(postcode);
        address.setCountry(country);
        return address;
    }
}