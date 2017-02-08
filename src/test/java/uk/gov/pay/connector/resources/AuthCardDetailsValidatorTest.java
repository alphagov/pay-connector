package uk.gov.pay.connector.resources;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.AuthCardDetails;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.util.AuthUtils.*;

public class AuthCardDetailsValidatorTest {

    private String validCVC = "999";
    private String validCardNumber = "4242424242424242";
    private String validExpiryDate = "12/99";
    private String cardBrand = "card-brand";

    @Test
    public void validationSucceedForCorrectAuthorisationCardDetails() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor(validCardNumber, validCVC, validExpiryDate, cardBrand);
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedForCVCwith4Digits() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor("12345678901234", "1234", validExpiryDate, cardBrand);
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedFor4digitsCardNumber() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor("12345678901234", validCVC, validExpiryDate, cardBrand);
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsForMissingCVC() {
        AuthCardDetails wrongauthorisationDetails = buildAuthCardDetailsFor(validCardNumber, null, validExpiryDate, cardBrand);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(wrongauthorisationDetails));
    }

    @Test
    public void validationFailsForMissingCardNumber() {
        AuthCardDetails wrongauthorisationDetails = buildAuthCardDetailsFor(null, validCVC, validExpiryDate, cardBrand);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(wrongauthorisationDetails));
    }

    @Test
    public void validationFailsForMissingExpiryDate() {
        AuthCardDetails wrongauthorisationDetails = buildAuthCardDetailsFor(validCardNumber, validCVC, null, cardBrand);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(wrongauthorisationDetails));
    }

    @Test
    public void validationFailsForMissingCardTypeId() {
        AuthCardDetails wrongauthorisationDetails = buildAuthCardDetailsFor(validCardNumber, validCVC, validExpiryDate, null);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(wrongauthorisationDetails));
    }

    @Test
    public void validationFailsForEmptyFields() {
        AuthCardDetails wrongauthorisationDetails = buildAuthCardDetailsFor("", "", "", "");
        assertFalse(AuthCardDetailsValidator.isWellFormatted(wrongauthorisationDetails));
    }

    @Test
    public void validationFailsFor11digitsCardNumber() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor("12345678901", validCVC, validExpiryDate, cardBrand);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }
	
    @Test
    public void validationSucceedsFor12digitsCardNumber() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor("123456789012", validCVC, validExpiryDate, cardBrand);
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }
	
    @Test
    public void validationSucceedsFor19digitsCardNumber() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor("1234567890123456789", validCVC, validExpiryDate, cardBrand);
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }
	
    @Test
    public void validationFailsFor20digitsCardNumber() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor("12345678901234567890", validCVC, validExpiryDate, cardBrand);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsForCardNumberWithNonDigits() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor("123456789012345A", validCVC, validExpiryDate, cardBrand);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsForCVCwithNonDigits() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor(validCardNumber, "45A", validExpiryDate, cardBrand);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsForCVCwithMoreThan4Digits() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor(validCardNumber, "12345", validExpiryDate, cardBrand);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsForCVCwithLessThan3Digits() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor(validCardNumber, "12", validExpiryDate, cardBrand);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsForExpiryDateWithWrongFormat() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor(validCardNumber, validCVC, "1290", cardBrand);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }


    @Test
    public void validationFailsForMissingAddress() {
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor(validCardNumber, validCVC, "1290", cardBrand, null);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }


    @Test
    public void validationFailsForMissingCityAddress() {
        Address address = addressFor("L1", null, "WJWHE", "GB");
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor(validCardNumber, validCVC, "1290", cardBrand, address);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsForMissingLine1Address() {
        Address address = addressFor(null, "London", "WJWHE", "GB");
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor(validCardNumber, validCVC, "1290", cardBrand, address);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsForMissingCountryAddress() {
        Address address = addressFor("L1", "London", "WJWHE", null);
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor(validCardNumber, validCVC, "1290", cardBrand, address);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsForMissingPostcodeAddress() {
        Address address = addressFor("L1", "London", null, "GB");
        cardBrand = "card-brand";
        AuthCardDetails authCardDetails = buildAuthCardDetailsFor(validCVC, "1290", cardBrand, cardBrand, address);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    private AuthCardDetails buildAuthCardDetailsFor(String cardNo, String cvc, String expiry, String cardBrand) {
        return buildAuthCardDetailsFor(cardNo, cvc, expiry, cardBrand, goodAddress());
    }

    private AuthCardDetails buildAuthCardDetailsFor(String cardNo, String cvc, String expiry, String cardBrand, Address address) {
        return buildAuthCardDetails("Mr. Payment", cardNo, cvc, expiry, cardBrand, address);
    }
}
