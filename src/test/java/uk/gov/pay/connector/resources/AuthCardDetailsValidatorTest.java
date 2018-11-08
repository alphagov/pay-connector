package uk.gov.pay.connector.resources;

import org.junit.Test;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.common.validator.AuthCardDetailsValidator;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.util.AuthUtils.aValidAuthorisationDetails;
import static uk.gov.pay.connector.util.AuthUtils.addressFor;
import static uk.gov.pay.connector.util.AuthUtils.buildAuthCardDetails;
import static uk.gov.pay.connector.util.AuthUtils.goodAddress;

public class AuthCardDetailsValidatorTest {

    private String validCVC = "999";
    private String validCardNumber = "4242424242424242";
    private String validExpiryDate = "12/99";
    private String cardBrand = "card-brand";

    private String sneakyCardNumber = "this12card3number4is5hidden6;7 89-0(1+2.";

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

    @Test
    public void validationFailsIfCardHolderContainsMoreThanElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setCardHolder(sneakyCardNumber);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedsIfCardHolderContainsExactlyElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setCardHolder("1 Mr John 123456789 Smith 0");
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsIfCardBrandContainsMoreThanElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setCardBrand(sneakyCardNumber);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedsIfCardBrandContainsExactlyElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setCardBrand("12345678901");
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }
    
    @Test
    public void validationFailsIfAddressLine1ContainsMoreThanElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setLine1(sneakyCardNumber);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedIfAddressLine1ContainsExactlyElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setLine2("01234/5678 90th Street");
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsIfAddressLine2ContainsMoreThanElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setLine2(sneakyCardNumber);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedIfAddressLine2ContainsExactlyElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setLine2("01234/5678 90th Street");
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedIfAddressLine2IsNull() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setLine2(null);
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedIfAddressIsNull() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setAddress(null);
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsIfPostCodeContainsMoreThanElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setPostcode(sneakyCardNumber);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedsIfPostCodeContainsExactlyElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setPostcode("12N3446789M01");
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsIfCityContainsMoreThanElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setCity(sneakyCardNumber);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedsIfCityContainsExactlyElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setCity("12N3446789M01");
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsIfCountyMoreThanElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setCounty(sneakyCardNumber);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedsIfCountyContainsExactlyElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setCounty("12N3446789M01");
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedsIfCountyIsNull() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setCounty(null);
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsIfCountryMoreThanElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setCountry(sneakyCardNumber);
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedsIfCountryContainsExactlyElevenDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.getAddress().setCountry("12N3446789M01");
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsIfCardHolderIsThreeDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setCardHolder("555");
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsIfCardHolderIsFourDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setCardHolder("5678");
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsIfCardHolderIsThreeDigitsSurroundedByWhitespace() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setCardHolder(" \t 321 ");
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationFailsIfCardHolderIsFourDigitsSurroundedByWhitespace() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setCardHolder(" 1234 \t");
        assertFalse(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedsIfCardHolderContainsThreeDigitsSurroundedByNonWhitespace() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setCardHolder("Ms 333");
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedsIfCardHolderContainsFourDigitsSurroundedByNonWhitespace() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setCardHolder("1234 Jr.");
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedsIfCardHolderContainsTwoDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setCardHolder("22");
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    @Test
    public void validationSucceedsIfCardHolderContainsFiveDigits() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        authCardDetails.setCardHolder("12345");
        assertTrue(AuthCardDetailsValidator.isWellFormatted(authCardDetails));
    }

    private AuthCardDetails buildAuthCardDetailsFor(String cardNo, String cvc, String expiry, String cardBrand) {
        return buildAuthCardDetailsFor(cardNo, cvc, expiry, cardBrand, goodAddress());
    }

    private AuthCardDetails buildAuthCardDetailsFor(String cardNo, String cvc, String expiry, String cardBrand, Address address) {
        return buildAuthCardDetails("Mr. Payment", cardNo, cvc, expiry, cardBrand, address);
    }
}
