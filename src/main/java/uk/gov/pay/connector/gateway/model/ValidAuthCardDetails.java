package uk.gov.pay.connector.gateway.model;

import uk.gov.pay.connector.common.validator.AuthCardDetailsValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ TYPE, ANNOTATION_TYPE })
@Retention(RUNTIME)
@Constraint(validatedBy = { AuthCardDetailsValidator.class })
@Documented
public @interface ValidAuthCardDetails {
    String message() default "Values do not match expected format/length.";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };
}
