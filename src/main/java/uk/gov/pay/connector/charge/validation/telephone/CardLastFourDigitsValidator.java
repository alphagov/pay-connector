package uk.gov.pay.connector.charge.validation.telephone;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class CardLastFourDigitsValidator implements ConstraintValidator<ValidCardExpiryDate, String> {

    private Pattern pattern = Pattern.compile("\\d{4}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        
        if (value == null) {
            return false;
        }

        return pattern.matcher(value).matches();

    }
}
