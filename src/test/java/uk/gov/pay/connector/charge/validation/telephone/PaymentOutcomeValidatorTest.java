package uk.gov.pay.connector.charge.validation.telephone;

import org.junit.BeforeClass;
import org.junit.Test;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.charge.model.telephone.Supplemental;
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class PaymentOutcomeValidatorTest {
    
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
                .withCardExpiry(CardExpiryDate.valueOf("01/99"))
                .withCardType("visa")
                .withLastFourDigits("1234")
                .withFirstSixDigits("123456");
                
    }

    @Test
    public void failsValidationForInvalidPaymentOutcomeStatus() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(new PaymentOutcome("invalid"))
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage(), is("Field [payment_outcome] must include a valid status and error code"));
    }

    @Test
    public void failsValidationForPaymentOutcomeStatusSuccessAndErrorCodeGiven() {

        PaymentOutcome paymentOutcome = new PaymentOutcome(
                "success",
                "error",
                new Supplemental(
                        "ECKOH01234",
                        "textual message describing error code"
                )
        );
        
        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage(), is("Field [payment_outcome] must include a valid status and error code"));
    }

    @Test
    public void failsValidationForInvalidErrorCode() {

        PaymentOutcome paymentOutcome = new PaymentOutcome(
                "failed",
                "error",
                new Supplemental(
                        "ECKOH01234",
                        "textual message describing error code"
                )
        );

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage(), is("Field [payment_outcome] must include a valid status and error code"));
    }

    @Test
    public void passesValidationForCorrectErrorCode() {

        PaymentOutcome paymentOutcome = new PaymentOutcome(
                "failed",
                "P0010",
                new Supplemental(
                        "ECKOH01234",
                        "textual message describing error code"
                )
        );

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(paymentOutcome)
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.isEmpty(), is(true));
    }

    @Test
    public void passesValidationForPaymentOutcomeStatusOfSuccess() {
        
        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(new PaymentOutcome("success"))
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.isEmpty(), is(true));
    }

    @Test
    public void passesValidationForNullPaymentOutcome() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .withPaymentOutcome(null)
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage(), is("Field [payment_outcome] cannot be null"));
    }
}
