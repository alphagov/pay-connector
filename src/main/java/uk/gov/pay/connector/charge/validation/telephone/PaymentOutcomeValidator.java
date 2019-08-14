package uk.gov.pay.connector.charge.validation.telephone;

import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.HashSet;

public class PaymentOutcomeValidator implements ConstraintValidator<ValidPaymentOutcome, PaymentOutcome> {

    private final static HashSet<String> ERROR_CODES = new HashSet<>();

    static {
        ERROR_CODES.add("P0010");
        ERROR_CODES.add("P0030");
        ERROR_CODES.add("P0050");
    }
    
    @Override
    public boolean isValid(PaymentOutcome paymentOutcome, ConstraintValidatorContext context) {
        
        if(paymentOutcome.getStatus() == null) {
            return false;
        }
        
        if(paymentOutcome.getStatus().equals("success") && paymentOutcome.getCode() == null) {
            return true;
        }
        
        return paymentOutcome.getStatus().equals("failed") && (ERROR_CODES.contains(paymentOutcome.getCode()));
    }
}
