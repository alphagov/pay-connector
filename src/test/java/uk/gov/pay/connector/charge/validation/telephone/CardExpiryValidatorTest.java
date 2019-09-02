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

public class CardExpiryValidatorTest {
    
    private static TelephoneChargeCreateRequest.Builder telephoneRequestBuilder = new TelephoneChargeCreateRequest.Builder();

    private static Validator validator;

    @BeforeClass
    public static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        
        telephoneRequestBuilder
                .withAmount(1200L)
                .withDescription("Some description")
                .withReference("Some reference")
                .withProcessorId("1PROC")
                .withProviderId("1PROV")
                .withLastFourDigits("1234")
                .withFirstSixDigits("123456")
                .withCardType("visa")
                .withPaymentOutcome(new PaymentOutcome("success"));
    }

    @Test
    public void failsValidationForInvalidMonth00() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withCardExpiry("00/99")
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), is(1));
        assertThat(constraintViolations.iterator().next().getMessage(), is("Field [card_expiry] must have valid MM/YY"));
    }
    
    @Test
    public void failsValidationForInvalidMonth99() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withCardExpiry("99/99")
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage(), is("Field [card_expiry] must have valid MM/YY"));
    }

    @Test
    public void passesValidationForValidCardExpiry() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withCardExpiry("01/99")
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.isEmpty(), is(true));
    }

    @Test
    public void passesValidationForNullCardExpiry() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withCardExpiry(null)
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage(), is("Field [card_expiry] cannot be null"));
    }
}
