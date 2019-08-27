package uk.gov.pay.connector.charge.validation.telephone;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ FIELD})
@Retention(RUNTIME)
@Constraint(validatedBy = CardTypeValidator.class)
@Documented
public @interface ValidCardType {
    
    String message() default "Card type must be either master-card, visa, maestro, diners-club or american-express";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
