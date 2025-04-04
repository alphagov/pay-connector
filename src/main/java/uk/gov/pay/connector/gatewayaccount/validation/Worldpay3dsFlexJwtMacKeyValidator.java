package uk.gov.pay.connector.gatewayaccount.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class Worldpay3dsFlexJwtMacKeyValidator implements ConstraintValidator<ValidWorldpay3dsFlexJwtMacKey, String> {

    private static final Pattern LOWER_CASE_UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return isNotBlank(value) && LOWER_CASE_UUID.matcher(value).matches();
    }
}
