package uk.gov.pay.connector.charge.validation.telephone;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class CardLastFourDigitsValidator implements ConstraintValidator<ValidCardLastFourDigits, String> {

    private Pattern pattern = Pattern.compile("[0-9]{4}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        if(value == null) {
            return true;
        }
        
        return pattern.matcher(value).matches();
    }
}
