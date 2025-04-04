package uk.gov.pay.connector.charge.validation.telephone;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = PaymentOutcomeValidator.class)
@Documented
public @interface ValidPaymentOutcome {

    String message() default "Must include a valid status and error code";

    Class<?>[] groups() default{};

    Class<? extends Payload>[] payload() default{};
    
}
