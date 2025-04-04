package uk.gov.pay.connector.charge.validation.telephone;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ FIELD })
@Retention(RUNTIME)
@Constraint(validatedBy = CardLastFourDigitsValidator.class)
@Documented
public @interface ValidCardLastFourDigits {

    String message() default "Must be exactly 4 digits";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
