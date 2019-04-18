package uk.gov.pay.connector.util;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Map;
import java.util.Objects;

public class MapValueAsStringLengthValidator implements ConstraintValidator<MapValueLength, Map<String, Object>> {

    private int max;
    private int min;

    @Override
    public void initialize(MapValueLength constraintAnnotation) {
        max = constraintAnnotation.max();
        min = constraintAnnotation.min();
    }

    @Override
    public boolean isValid(Map<String, Object> theMap, ConstraintValidatorContext context) {
        if (theMap == null) {
            return true;
        }

        return theMap.values().stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .noneMatch(value -> value.length() < min || value.length() > max);
    }
}
