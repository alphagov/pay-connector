package uk.gov.pay.connector.resources;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.AuthorisationDetails;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.util.CardUtils.*;

public class AuthorisationDetailsValidatorTest {

    private String validCVC = "999";
    private String validCardNumber = "4242424242424242";
    private String validExpiryDate = "12/99";
    private String cardBrand = "card-brand";

    @Test
    public void validationSucceedForCorrectAuthorisationDetails() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor(validCardNumber, validCVC, validExpiryDate, cardBrand);
        assertTrue(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }

    @Test
    public void validationSucceedForCVCwith4Digits() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor("12345678901234", "1234", validExpiryDate, cardBrand);
        assertTrue(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }

    @Test
    public void validationSucceedFor4digitsCardNumber() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor("12345678901234", validCVC, validExpiryDate, cardBrand);
        assertTrue(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }

    @Test
    public void validationFailsForMissingCVC() {
        AuthorisationDetails wrongauthorisationDetails = buildAuthorisationDetailsFor(validCardNumber, null, validExpiryDate, cardBrand);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(wrongauthorisationDetails));
    }

    @Test
    public void validationFailsForMissingCardNumber() {
        AuthorisationDetails wrongauthorisationDetails = buildAuthorisationDetailsFor(null, validCVC, validExpiryDate, cardBrand);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(wrongauthorisationDetails));
    }

    @Test
    public void validationFailsForMissingExpiryDate() {
        AuthorisationDetails wrongauthorisationDetails = buildAuthorisationDetailsFor(validCardNumber, validCVC, null, cardBrand);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(wrongauthorisationDetails));
    }

    @Test
    public void validationFailsForMissingCardTypeId() {
        AuthorisationDetails wrongauthorisationDetails = buildAuthorisationDetailsFor(validCardNumber, validCVC, validExpiryDate, null);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(wrongauthorisationDetails));
    }

    @Test
    public void validationFailsForEmptyFields() {
        AuthorisationDetails wrongauthorisationDetails = buildAuthorisationDetailsFor("", "", "", "");
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(wrongauthorisationDetails));
    }

    @Test
    public void validationFailsFor11digitsCardNumber() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor("12345678901", validCVC, validExpiryDate, cardBrand);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }
	
    @Test
    public void validationSucceedsFor12digitsCardNumber() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor("123456789012", validCVC, validExpiryDate, cardBrand);
        assertTrue(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }
	
    @Test
    public void validationSucceedsFor19digitsCardNumber() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor("1234567890123456789", validCVC, validExpiryDate, cardBrand);
        assertTrue(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }
	
    @Test
    public void validationFailsFor20digitsCardNumber() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor("12345678901234567890", validCVC, validExpiryDate, cardBrand);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }

    @Test
    public void validationFailsForCardNumberWithNonDigits() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor("123456789012345A", validCVC, validExpiryDate, cardBrand);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }

    @Test
    public void validationFailsForCVCwithNonDigits() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor(validCardNumber, "45A", validExpiryDate, cardBrand);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }

    @Test
    public void validationFailsForCVCwithMoreThan4Digits() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor(validCardNumber, "12345", validExpiryDate, cardBrand);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }

    @Test
    public void validationFailsForCVCwithLessThan3Digits() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor(validCardNumber, "12", validExpiryDate, cardBrand);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }

    @Test
    public void validationFailsForExpiryDateWithWrongFormat() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor(validCardNumber, validCVC, "1290", cardBrand);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }


    @Test
    public void validationFailsForMissingAddress() {
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor(validCardNumber, validCVC, "1290", cardBrand, null);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }


    @Test
    public void validationFailsForMissingCityAddress() {
        Address address = addressFor("L1", null, "WJWHE", "GB");
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor(validCardNumber, validCVC, "1290", cardBrand, address);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }

    @Test
    public void validationFailsForMissingLine1Address() {
        Address address = addressFor(null, "London", "WJWHE", "GB");
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor(validCardNumber, validCVC, "1290", cardBrand, address);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }

    @Test
    public void validationFailsForMissingCountryAddress() {
        Address address = addressFor("L1", "London", "WJWHE", null);
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor(validCardNumber, validCVC, "1290", cardBrand, address);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }

    @Test
    public void validationFailsForMissingPostcodeAddress() {
        Address address = addressFor("L1", "London", null, "GB");
        cardBrand = "card-brand";
        AuthorisationDetails authorisationDetails = buildAuthorisationDetailsFor(validCVC, "1290", cardBrand, cardBrand, address);
        assertFalse(AuthorisationDetailsValidator.isWellFormatted(authorisationDetails));
    }

    private AuthorisationDetails buildAuthorisationDetailsFor(String cardNo, String cvc, String expiry, String cardBrand) {
        return buildAuthorisationDetailsFor(cardNo, cvc, expiry, cardBrand, goodAddress());
    }

    private AuthorisationDetails buildAuthorisationDetailsFor(String cardNo, String cvc, String expiry, String cardBrand, Address address) {
        return buildAuthorisationDetails("Mr. Payment", cardNo, cvc, expiry, cardBrand, address);
    }
}
