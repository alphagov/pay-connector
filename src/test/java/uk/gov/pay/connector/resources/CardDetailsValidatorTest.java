package uk.gov.pay.connector.resources;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.AddressEntity;
import uk.gov.pay.connector.model.domain.Card;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.util.CardUtils.*;

public class CardDetailsValidatorTest {

    private String validCVC = "999";
    private String validCardNumber = "4242424242424242";
    private String validExpiryDate = "12/99";
    private String cardBrand = "card-brand";

    @Test
    public void validationSucceedForCorrectCardDetails() {
        Card cardDetails = buildCardDetailsFor(validCardNumber, validCVC, validExpiryDate, cardBrand);
        assertTrue(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationSucceedForCVCwith4Digits() {
        Card cardDetails = buildCardDetailsFor("12345678901234", "1234", validExpiryDate, cardBrand);
        assertTrue(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationSucceedFor4digitsCardNumber() {
        Card cardDetails = buildCardDetailsFor("12345678901234", validCVC, validExpiryDate, cardBrand);
        assertTrue(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForMissingCVC() {
        Card wrongCardDetails = buildCardDetailsFor(validCardNumber, null, validExpiryDate, cardBrand);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsForMissingCardNumber() {
        Card wrongCardDetails = buildCardDetailsFor(null, validCVC, validExpiryDate, cardBrand);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsForMissingExpiryDate() {
        Card wrongCardDetails = buildCardDetailsFor(validCardNumber, validCVC, null, cardBrand);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsForMissingCardTypeId() {
        Card wrongCardDetails = buildCardDetailsFor(validCardNumber, validCVC, validExpiryDate, null);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsForEmptyFields() {
        Card wrongCardDetails = buildCardDetailsFor("", "", "", "");
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(wrongCardDetails));
    }

    @Test
    public void validationFailsFor13digitsCardNumber() {
        Card cardDetails = buildCardDetailsFor("1234567890123", validCVC, validExpiryDate, cardBrand);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForCardNumberWithNonDigits() {
        Card cardDetails = buildCardDetailsFor("123456789012345A", validCVC, validExpiryDate, cardBrand);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForCVCwithNonDigits() {
        Card cardDetails = buildCardDetailsFor(validCardNumber, "45A", validExpiryDate, cardBrand);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForCVCwithMoreThan4Digits() {
        Card cardDetails = buildCardDetailsFor(validCardNumber, "12345", validExpiryDate, cardBrand);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForCVCwithLessThan3Digits() {
        Card cardDetails = buildCardDetailsFor(validCardNumber, "12", validExpiryDate, cardBrand);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForExpiryDateWithWrongFormat() {
        Card cardDetails = buildCardDetailsFor(validCardNumber, validCVC, "1290", cardBrand);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }


    @Test
    public void validationFailsForMissingAddress() {
        Card cardDetails = buildCardDetailsFor(validCardNumber, validCVC, "1290", cardBrand, null);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }


    @Test
    public void validationFailsForMissingCityAddress() {
        AddressEntity addressEntity = addressFor("L1", null, "WJWHE", "GB");
        Card cardDetails = buildCardDetailsFor(validCardNumber, validCVC, "1290", cardBrand, addressEntity);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForMissingLine1Address() {
        AddressEntity addressEntity = addressFor(null, "London", "WJWHE", "GB");
        Card cardDetails = buildCardDetailsFor(validCardNumber, validCVC, "1290", cardBrand, addressEntity);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForMissingCountryAddress() {
        AddressEntity addressEntity = addressFor("L1", "London", "WJWHE", null);
        Card cardDetails = buildCardDetailsFor(validCardNumber, validCVC, "1290", cardBrand, addressEntity);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    @Test
    public void validationFailsForMissingPostcodeAddress() {
        AddressEntity addressEntity = addressFor("L1", "London", null, "GB");
        cardBrand = "card-brand";
        Card cardDetails = buildCardDetailsFor(validCVC, "1290", cardBrand, cardBrand, addressEntity);
        assertFalse(CardDetailsValidator.isWellFormattedCardDetails(cardDetails));
    }

    private Card buildCardDetailsFor(String cardNo, String cvc, String expiry, String cardBrand) {
        return buildCardDetailsFor(cardNo, cvc, expiry, cardBrand, goodAddress());
    }

    private Card buildCardDetailsFor(String cardNo, String cvc, String expiry, String cardBrand, AddressEntity addressEntity) {
        return buildCardDetails("Mr. Payment", cardNo, cvc, expiry, cardBrand, addressEntity);
    }
}
