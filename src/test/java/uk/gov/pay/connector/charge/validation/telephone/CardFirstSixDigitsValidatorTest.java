package uk.gov.pay.connector.charge.validation.telephone;

import org.junit.BeforeClass;
import org.junit.Test;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class CardFirstSixDigitsValidatorTest {
    
    private static TelephoneChargeCreateRequest.ChargeBuilder telephoneRequestBuilder = new TelephoneChargeCreateRequest.ChargeBuilder();

    private static Validator validator;

    @BeforeClass
    public static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        telephoneRequestBuilder
                .amount(1200L)
                .description("Some description")
                .reference("Some reference")
                .processorId("1PROC")
                .providerId("1PROV")
                .cardExpiry("01/99")
                .lastFourDigits("1234")
                .cardType("visa")
                .paymentOutcome(new PaymentOutcome("success"));
    }

    @Test
    public void failsValidationForFiveDigits() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .firstSixDigits("12345")
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage(), is("Field [first_six_digits] must be exactly 6 digits"));
    }

    @Test
    public void failsValidationForSevenDigits() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .firstSixDigits("1234567")
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage(), is("Field [first_six_digits] must be exactly 6 digits"));
    }

    @Test
    public void passesValidationForSixDigits() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .firstSixDigits("123456")
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.isEmpty(), is(true));
    }

    @Test
    public void passesValidationForNullDigits() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .firstSixDigits(null)
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage().equals("Field [first_six_digits] must be exactly 6 digits"), is(false));
    }
}
