package uk.gov.pay.connector.charge.validation;

import uk.gov.pay.connector.gateway.PaymentGatewayName;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class PaymentProviderValidator implements ConstraintValidator<ValidPaymentProvider, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        
        return PaymentGatewayName.isValidPaymentGateway(value);
    }
}
