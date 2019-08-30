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

public class CardTypeValidatorTest {
    
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
                .withCardExpiry("01/99")
                .withLastFourDigits("1234")
                .withFirstSixDigits("123456")
                .withPaymentOutcome(new PaymentOutcome("success"));
    }
    
    @Test
    public void failsValidationForInvalidCardType() {
        
        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withCardType("bad-card")
                .build();
        
        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage(), is("Field [card_type] must be either master-card, visa, maestro, diners-club or american-express"));
    }

    @Test
    public void passesValidationForValidCardType() {
        
        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withCardType("visa")
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.isEmpty(), is(true));
    }

    @Test
    public void passesValidationForNullCardType() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withCardType(null)
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage().equals("Field [card_type] must be either master-card, visa, maestro, diners-club or american-express"), is(false));
    }
}
