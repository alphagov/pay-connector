package uk.gov.pay.connector.util;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MapValueTypesValidator implements ConstraintValidator<MapValueTypes, Map<String, Object>> {

    private Set<Class> allowedTypes;

    @Override
    public void initialize(MapValueTypes constraintAnnotation) {
        allowedTypes = Set.of(constraintAnnotation.types());
    }

    @Override
    public boolean isValid(Map<String, Object> theMap, ConstraintValidatorContext context) {
        if (theMap == null) {
            return true;
        }

        return theMap.values().stream()
                .filter(Objects::nonNull)
                .map(Object::getClass)
                .allMatch(this::validType);
    }

    private boolean validType(Class clazz) {
        return allowedTypes.stream().anyMatch(permittedClass -> permittedClass.isAssignableFrom(clazz));
    }
}
