package uk.gov.pay.connector.charge.validation.telephone;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.HashSet;

public class CardBrandValidator implements ConstraintValidator<ValidCardBrand, String> {
    private final static HashSet<String> CARD_BRANDS = new HashSet<>();

    static {
        CARD_BRANDS.add("master-card");
        CARD_BRANDS.add("visa");
        CARD_BRANDS.add("maestro");
        CARD_BRANDS.add("diners-club");
        CARD_BRANDS.add("american-express");
        CARD_BRANDS.add("jcb");
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        if(value == null) {
            return true;
        }

        return CARD_BRANDS.contains(value);
    }
}
