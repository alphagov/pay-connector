package uk.gov.pay.connector.charge.validation;

import uk.gov.pay.connector.charge.validation.telephone.CardBrandValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ FIELD})
@Retention(RUNTIME)
@Constraint(validatedBy = PaymentProviderValidator.class)
@Documented
public @interface ValidPaymentProvider {

    String message() default "Field [payment_provider] must be one of [epdq, sandbox, stripe, worldpay]";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
    
}
