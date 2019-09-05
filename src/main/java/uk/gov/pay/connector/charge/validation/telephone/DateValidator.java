package uk.gov.pay.connector.charge.validation.telephone;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

public class DateValidator implements ConstraintValidator<ValidDate, String> {
    
    @Override
    public boolean isValid(String date, ConstraintValidatorContext context) {
        
        if (date == null) {
            return true;
        }
        
        try {
            ZonedDateTime.parse(date);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
