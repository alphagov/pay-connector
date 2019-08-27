package uk.gov.pay.connector.charge.validation.telephone;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.HashSet;

public class CardTypeValidator implements ConstraintValidator<ValidCardType, String> {
    private final static HashSet<String> CARD_TYPES = new HashSet<>();

    static {
        CARD_TYPES.add("master-card");
        CARD_TYPES.add("visa");
        CARD_TYPES.add("maestro");
        CARD_TYPES.add("diners-club");
        CARD_TYPES.add("american-express");
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        return CARD_TYPES.contains(value);
    }
}
