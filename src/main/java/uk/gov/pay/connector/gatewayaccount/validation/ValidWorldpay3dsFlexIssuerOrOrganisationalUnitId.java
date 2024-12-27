package uk.gov.pay.connector.gatewayaccount.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = Worldpay3dsFlexIssuerOrOrganisationalUnitIdValidator.class)
public @interface ValidWorldpay3dsFlexIssuerOrOrganisationalUnitId {
    String message();
    Class<?>[] groups() default { };
    Class<? extends Payload>[] payload() default { };
}
