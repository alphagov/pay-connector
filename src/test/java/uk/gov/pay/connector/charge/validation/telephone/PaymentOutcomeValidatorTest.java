package uk.gov.pay.connector.charge.validation.telephone;

import org.junit.BeforeClass;
import org.junit.Test;
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
                .createdDate("2018-02-21T16:04:25Z")
                .authorisedDate("2018-02-21T16:05:33Z")
                .authCode("666")
                .processorId("1PROC")
                .providerId("1PROV")
                .cardExpiry("01/99")
                .cardType("visa")
                .lastFourDigits("1234")
                .firstSixDigits("123456")
                .nameOnCard("Jane Doe")
                .emailAddress("jane_doe@example.com")
                .telephoneNumber("+447700900796");
                
    }

    @Test
    public void failsValidationForInvalidPaymentOutcomeStatus() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .paymentOutcome(new PaymentOutcome("invalid"))
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
                .paymentOutcome(paymentOutcome)
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
                .paymentOutcome(paymentOutcome)
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
                .paymentOutcome(paymentOutcome)
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.isEmpty(), is(true));
    }

    @Test
    public void passesValidationForPaymentOutcomeStatusOfSuccess() {
        
        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .paymentOutcome(new PaymentOutcome("success"))
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.isEmpty(), is(true));
    }

    @Test
    public void passesValidationForNullPaymentOutcome() {

        TelephoneChargeCreateRequest telephoneChargeCreateRequest = telephoneRequestBuilder
                .paymentOutcome(null)
                .build();

        Set<ConstraintViolation<TelephoneChargeCreateRequest>> constraintViolations = validator.validate(telephoneChargeCreateRequest);

        assertThat(constraintViolations.size(), isNumber(1));
        assertThat(constraintViolations.iterator().next().getMessage().equals("Field [payment_outcome] must include a valid status and error code"), is(false));
    }
}
