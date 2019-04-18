package uk.gov.pay.connector.util;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Map;

public class MapKeyLengthValidator implements ConstraintValidator<MapKeyLength, Map<String, Object>> {

    private int max;
    private int min;

    @Override
    public void initialize(MapKeyLength constraintAnnotation) {
        max = constraintAnnotation.max();
        min = constraintAnnotation.min();
    }

    @Override
    public boolean isValid(Map<String, Object> theMap, ConstraintValidatorContext context) {
        if (theMap == null) {
            return true;
        }

        return theMap.keySet().stream()
                .noneMatch(key -> key.length() < min || key.length() > max);
    }
}
