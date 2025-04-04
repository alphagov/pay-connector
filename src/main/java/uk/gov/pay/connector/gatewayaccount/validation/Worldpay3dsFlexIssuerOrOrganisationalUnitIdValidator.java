package uk.gov.pay.connector.gatewayaccount.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class Worldpay3dsFlexIssuerOrOrganisationalUnitIdValidator implements ConstraintValidator<ValidWorldpay3dsFlexIssuerOrOrganisationalUnitId, String> {

    private static final Pattern TWENTY_FOUR_LOWER_CASE_HEXADECIMAL_CHARACTERS = Pattern.compile("[0-9a-f]{24}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return isNotBlank(value) && TWENTY_FOUR_LOWER_CASE_HEXADECIMAL_CHARACTERS.matcher(value).matches();
    }
}
