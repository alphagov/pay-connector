package uk.gov.pay.connector.unit.resources;

import org.junit.Test;
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
        assertTrue(CardDetailsValidator.isValidCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForMissingCVC() {
        Map<String, Object> wrongCardDetails = new HashMap<>();
        wrongCardDetails.put("card_number", validCardNumber);
        wrongCardDetails.put("expiry_date", validExpiryDate);

        assertFalse(CardDetailsValidator.isValidCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsForMissingCardNumber() {
        Map<String, Object> wrongCardDetails = new HashMap<>();
        wrongCardDetails.put("cvc", validCVC);
        wrongCardDetails.put("expiry_date", validExpiryDate);

        assertFalse(CardDetailsValidator.isValidCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsForMissingExpiryDate() {
        Map<String, Object> wrongCardDetails = new HashMap<>();
        wrongCardDetails.put("cvc", validCVC);
        wrongCardDetails.put("card_number", validCardNumber);

        assertFalse(CardDetailsValidator.isValidCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsForEmptyFields() {
        Map<String, Object> wrongCardDetails = buildCardDetails("", "", "");
        assertFalse(CardDetailsValidator.isValidCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsFor15digitsCardNumber() {
        Map<String, Object> cardDetails = buildCardDetails("123456789012345", validCVC, validExpiryDate);
        assertFalse(CardDetailsValidator.isValidCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForCardNumberWithNonDigits() {
        Map<String, Object> cardDetails = buildCardDetails("123456789012345A", validCVC, validExpiryDate);
        assertFalse(CardDetailsValidator.isValidCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForCVCwithNonDigits() {
        Map<String, Object> cardDetails = buildCardDetails(validCardNumber, "45A", validExpiryDate);
        assertFalse(CardDetailsValidator.isValidCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForCVCwithMoreThan3Digits() {
        Map<String, Object> cardDetails = buildCardDetails(validCardNumber, "4444", validExpiryDate);
        assertFalse(CardDetailsValidator.isValidCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForExpiryDateWithWrongFormat() {
        Map<String, Object> cardDetails = buildCardDetails(validCardNumber, validCVC, "1290");
        assertFalse(CardDetailsValidator.isValidCardDetails(cardDetails));
    }

    private Map<String, Object> buildCardDetails(String cardNumber, String cvc, String expiryDate) {
        Map<String, Object> cardDetails = new HashMap<>();
        cardDetails.put("card_number", cardNumber);
        cardDetails.put("cvc", cvc);
        cardDetails.put("expiry_date", expiryDate);
        return cardDetails;
    }
}