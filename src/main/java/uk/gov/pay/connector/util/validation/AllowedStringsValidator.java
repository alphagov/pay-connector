package uk.gov.pay.connector.util.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Set;

public class AllowedStringsValidator implements ConstraintValidator<AllowedStrings, String>  {

    private Set<String> allowedStrings;
    private String errorMessage;
    private boolean useCustomErrorMessage = false;
    @Override
    public void initialize(AllowedStrings parameters) {
        allowedStrings = Set.of(parameters.allowed());

        if (parameters.message().isEmpty()) {
            useCustomErrorMessage = true;
            switch(allowedStrings.size()) {
                case 0: errorMessage = String.format("The '%s' field must be empty", parameters.fieldName()); break;
                case 1: errorMessage = String.format(
                        "The '%s' field must be '%s'",
                        parameters.fieldName(),
                        parameters.allowed()[0]
                ); break;
                default: errorMessage = String.format(
                        "The '%s' field must be one of: [%s]",
                        parameters.fieldName(),
                        String.join(", ", parameters.allowed())
                );
            }
            
        }
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (useCustomErrorMessage) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(errorMessage).addConstraintViolation();
        }
        
        if (value == null) return false;
        return allowedStrings.contains(value);
    }
}
