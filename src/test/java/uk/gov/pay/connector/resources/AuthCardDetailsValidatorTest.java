package uk.gov.pay.connector.resources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.model.domain.AddressFixture;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthCardDetailsValidatorTest {

    private String sneakyCardNumber = "this12card3number4is5hidden6;7 89-0(1+2.";
    private String over255LongString = "ThisLineIs_TooLong_cbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsaacbstrdnsa1234567899";
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validationSucceedForWellFormattedAuthorisationCardDetails() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationSucceedForCVCwith4Digits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCvc("1234")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationSucceedFor14digitsCardNumber() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardNo("12345678901234")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationFailsForMissingCVC() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCvc(null)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsForMissingCardNumber() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardNo(null)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsForMissingCardBrand() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardBrand(null)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsForEmptyFields() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardNo("")
                .withCvc("")
                .withCardBrand("")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsFor11digitsCardNumber() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardNo("12345678901")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationSucceedsFor12digitsCardNumber() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardNo("123456789012")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationSucceedsFor19digitsCardNumber() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardNo("1234567890123456789")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationFailsFor20digitsCardNumber() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardNo("12345678901234567890")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsForCardNumberWithNonDigits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardNo("123456789012345A")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsForCVCwithNonDigits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCvc("45A")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsForCVCwithMoreThan4Digits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCvc("12345")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsForCVCwithLessThan3Digits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCvc("12")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsForMissingCityAddress() {
        Address address = AddressFixture.anAddress()
                .withCity(null)
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsForMissingLine1Address() {
        Address address = AddressFixture.anAddress()
                .withLine1(null)
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    public void validationFailsForMissingCountryAddress() {
        Address address = AddressFixture.anAddress()
                .withCountry(null)
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsForMissingPostcodeAddress() {
        Address address = AddressFixture.anAddress()
                .withPostcode(null)
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsIfCardHolderContainsMoreThanElevenDigits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder(sneakyCardNumber)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationSucceedsIfCardHolderContainsExactlyElevenDigits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("1 Mr John 123456789 Smith 0")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationFailsIfCardBrandContainsMoreThanElevenDigits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardBrand(sneakyCardNumber)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationSucceedsIfCardBrandContainsExactlyElevenDigits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardBrand("12345678901")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationFailsIfAddressLine1ContainsMoreThanElevenDigits() {
        Address address = AddressFixture.anAddress()
                .withLine1(sneakyCardNumber)
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationSucceedIfAddressLine1ContainsExactlyElevenDigits() {
        Address address = AddressFixture.anAddress()
                .withLine1("01234/5678 90th Street")
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationFailsIfAddressLine2ContainsMoreThanElevenDigits() {
        Address address = AddressFixture.anAddress()
                .withLine2(sneakyCardNumber)
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationSucceedIfAddressLine2ContainsExactlyElevenDigits() {
        Address address = AddressFixture.anAddress()
                .withLine2("01234/5678 90th Street")
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationSucceedIfAddressLine2IsNull() {
        Address address = AddressFixture.anAddress()
                .withLine2(null)
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationSucceedIfAddressIsNull() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(null)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationFailsIfPostCodeContainsMoreThanElevenDigits() {
        Address address = AddressFixture.anAddress()
                .withPostcode(sneakyCardNumber)
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationSucceedsIfPostCodeContainsExactlyElevenDigits() {
        Address address = AddressFixture.anAddress()
                .withPostcode("12N3446789M01")
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationFailsIfCityContainsMoreThanElevenDigits() {
        Address address = AddressFixture.anAddress()
                .withCity(sneakyCardNumber)
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationSucceedsIfCityContainsExactlyElevenDigits() {
        Address address = AddressFixture.anAddress()
                .withCity("12N3446789M01")
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationFailsIfCountyMoreThanElevenDigits() {
        Address address = AddressFixture.anAddress()
                .withCounty(sneakyCardNumber)
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationSucceedsIfCountyContainsExactlyElevenDigits() {
        Address address = AddressFixture.anAddress()
                .withCounty("12N3446789M01")
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationSucceedsIfCountyIsNull() {
        Address address = AddressFixture.anAddress()
                .withCounty(null)
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationFailsIfCountryMoreThanElevenDigits() {
        Address address = AddressFixture.anAddress()
                .withCountry(sneakyCardNumber)
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationSucceedsIfCountryContainsExactlyElevenDigits() {
        Address address = AddressFixture.anAddress()
                .withCountry("12N3446789M01")
                .build();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(address)
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationFailsIfCardHolderIsThreeDigits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("555")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsIfCardHolderIsFourDigits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("5678")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsIfCardHolderIsThreeDigitsSurroundedByWhitespace() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder(" \t 321 ")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsIfCardHolderIsFourDigitsSurroundedByWhitespace() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder(" 1234 \t")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationSucceedsIfCardHolderContainsThreeDigitsSurroundedByNonWhitespace() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("Ms 333")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationSucceedsIfCardHolderContainsFourDigitsSurroundedByNonWhitespace() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("1234 Jr.")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationSucceedsIfCardHolderContainsTwoDigits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("22")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationSucceedsIfCardHolderContainsFiveDigits() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("12345")
                .build();

        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(0, violations.size());
    }

    @Test
    void validationFailsIfCardHolderIsTooLong() {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().withCardHolder(over255LongString).build();
        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsIfAddressLineOneIsTooLong() {
        Address address = AddressFixture.anAddress().withLine1(over255LongString).build();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().withAddress(address).build();
        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsIfAddressLineTwoIsTooLong() {
        Address address = AddressFixture.anAddress().withLine2(over255LongString).build();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().withAddress(address).build();
        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsIfAddressCountyIsTooLong() {
        Address address = AddressFixture.anAddress().withCounty(over255LongString).build();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().withAddress(address).build();
        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsIfAddressCountryIsTooLong() {
        Address address = AddressFixture.anAddress().withCountry(over255LongString).build();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().withAddress(address).build();
        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }

    @Test
    void validationFailsIfAddressCityIsTooLong() {
        Address address = AddressFixture.anAddress().withCity(over255LongString).build();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().withAddress(address).build();
        Set<ConstraintViolation<AuthCardDetails>> violations = validator.validate(authCardDetails);
        assertEquals(1, violations.size());
        assertEquals("Values do not match expected format/length.", violations.iterator().next().getMessage());
    }
}
