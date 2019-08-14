package uk.gov.pay.connector.charge.validation.telephone;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class CardExpiryValidator implements ConstraintValidator<ValidCardExpiryDate, String> {
    
    private Pattern pattern = Pattern.compile("(0[1-9]|1[0-2])\\/\\d{2}");
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        
        if (value == null) {
            return false;
        }
        
        return pattern.matcher(value).matches();
        
    }
}
