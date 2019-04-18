package uk.gov.pay.connector.util;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Map;
import java.util.Objects;

public class MapValueNotNullValidator implements ConstraintValidator<MapValueNotNull, Map<String, Object>> {

    @Override
    public boolean isValid(Map<String, Object> theMap, ConstraintValidatorContext context) {
        if (theMap == null) {
            return true;
        }

        return theMap.values().stream()
                .noneMatch(Objects::isNull);
    }
}
