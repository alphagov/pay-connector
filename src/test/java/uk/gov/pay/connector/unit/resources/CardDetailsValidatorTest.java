package uk.gov.pay.connector.unit.resources;

import org.junit.Test;
import uk.gov.pay.connector.model.Card;
import uk.gov.pay.connector.resources.CardDetailsValidator;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CardDetailsValidatorTest {

    private String validCVC = "999";
    private String validCardNumber = "4242424242424242";
    private String validExpiryDate = "12/99";

    @Test
    public void validationSucceedForCorrectCardDetails() {
        Map<String, Object> cardDetails = buildCardDetails(validCardNumber, validCVC, validExpiryDate);
        assertTrue(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(cardDetails)));
    }

    @Test
    public void validationSucceedFor14digitsCardNumber() {
        Map<String, Object> cardDetails = buildCardDetails("12345678901234", validCVC, validExpiryDate);
        assertTrue(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(cardDetails)));
    }


    @Test
    public void validationFailsForMissingCVC() {
        Map<String, Object> wrongCardDetails = new HashMap<>();
        wrongCardDetails.put("card_number", validCardNumber);
        wrongCardDetails.put("expiry_date", validExpiryDate);

        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(wrongCardDetails)));
    }

    @Test
    public void validationFailsForMissingCardNumber() {
        Map<String, Object> wrongCardDetails = new HashMap<>();
        wrongCardDetails.put("cvc", validCVC);
        wrongCardDetails.put("expiry_date", validExpiryDate);

        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(wrongCardDetails)));
    }

    @Test
    public void validationFailsForMissingExpiryDate() {
        Map<String, Object> wrongCardDetails = new HashMap<>();
        wrongCardDetails.put("cvc", validCVC);
        wrongCardDetails.put("card_number", validCardNumber);

        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(wrongCardDetails)));
    }

    @Test
    public void validationFailsForEmptyFields() {
        Map<String, Object> wrongCardDetails = buildCardDetails("", "", "");
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(wrongCardDetails)));
    }

    @Test
    public void validationFailsFor13digitsCardNumber() {
        Map<String, Object> cardDetails = buildCardDetails("1234567890123", validCVC, validExpiryDate);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(cardDetails)));
    }

    @Test
    public void validationFailsForCardNumberWithNonDigits() {
        Map<String, Object> cardDetails = buildCardDetails("123456789012345A", validCVC, validExpiryDate);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(cardDetails)));
    }

    @Test
    public void validationFailsForCVCwithNonDigits() {
        Map<String, Object> cardDetails = buildCardDetails(validCardNumber, "45A", validExpiryDate);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(cardDetails)));
    }

    @Test
    public void validationFailsForCVCwithMoreThan3Digits() {
        Map<String, Object> cardDetails = buildCardDetails(validCardNumber, "4444", validExpiryDate);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(cardDetails)));
    }

    @Test
    public void validationFailsForExpiryDateWithWrongFormat() {
        Map<String, Object> cardDetails = buildCardDetails(validCardNumber, validCVC, "1290");
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(cardDetails)));
    }


    @Test
    public void validationFailsForMissingAddress() {
        Map<String, Object> cardDetails = buildCardDetails(validCardNumber, validCVC, "1290", null);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(cardDetails)));
    }


    @Test
    public void validationFailsForMissingCityAddress() {
        Map<String,Object> address = addressFor("L1", null, "WJWHE", "GB");
        Map<String, Object> cardDetails = buildCardDetails(validCardNumber, validCVC, "1290", address);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(cardDetails)));
    }

    @Test
    public void validationFailsForMissingLine1Address() {
        Map<String,Object> address = addressFor(null, "London", "WJWHE", "GB");
        Map<String, Object> cardDetails = buildCardDetails(validCardNumber, validCVC, "1290", address);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(cardDetails)));
    }

    @Test
    public void validationFailsForMissingCountryAddress() {
        Map<String,Object> address = addressFor("L1", "London", "WJWHE", null);
        Map<String, Object> cardDetails = buildCardDetails(validCardNumber, validCVC, "1290", address);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(cardDetails)));
    }

    @Test
    public void validationFailsForMissingPostcodeAddress() {
        Map<String,Object> address = addressFor("L1", "London", null, "GB");
        Map<String, Object> cardDetails = buildCardDetails(validCardNumber, validCVC, "1290", address);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(Card.getCardFromDetails(cardDetails)));
    }


    private Map<String, Object> buildCardDetails(String cardNumber, String cvc, String expiryDate) {
        return buildCardDetails(cardNumber, cvc, expiryDate, goodAddress());
    }

    private Map<String, Object> buildCardDetails(String cardNumber, String cvc, String expiryDate, Map<String, Object> address) {
        Map<String, Object> cardDetails = new HashMap<>();
        cardDetails.put("card_number", cardNumber);
        cardDetails.put("cvc", cvc);
        cardDetails.put("expiry_date", expiryDate);
        cardDetails.put("address", address);
        return cardDetails;
    }

    private Map<String, Object> goodAddress() {
        return addressFor("The Money Pool", "London", "DO11 4RS", "GB");
    }

    private Map<String, Object> addressFor(String line1, String city, String postcode, String country) {
        Map<String, Object> address = new HashMap<>();
        address.put("line1", line1);
        address.put("city", city);
        address.put("postcode", postcode);
        address.put("country", country);
        return address;
    }
}