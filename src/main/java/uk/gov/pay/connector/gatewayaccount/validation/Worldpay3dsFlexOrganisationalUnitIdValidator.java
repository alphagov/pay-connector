package uk.gov.pay.connector.gatewayaccount.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class Worldpay3dsFlexOrganisationalUnitIdValidator implements ConstraintValidator<ValidWorldpay3dsFlexOrganisationalUnitId, String> {

    private static final Pattern TWENTY_FOUR_LOWER_CASE_HEXADECIMAL_CHARACTERS = Pattern.compile("[0-9a-f]{24}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return isNotBlank(value) && TWENTY_FOUR_LOWER_CASE_HEXADECIMAL_CHARACTERS.matcher(value).matches();
    }
}
