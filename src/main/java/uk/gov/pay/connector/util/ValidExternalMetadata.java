package uk.gov.pay.connector.util;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = {})
@NotNull(message = "Field [metadata] must not be null")
@Size(max = 10, message = "Field [metadata] cannot have more than {max} key-value pairs")
@MapKeyLength(max = 30, min = 1, message = "Field [metadata] keys must be between {min} and {max} characters long")
@MapValueTypes(types = {String.class, Number.class, Boolean.class}, message = "Field [metadata] values must be of type String, Boolean or Number")
@MapValueLength(max = 50, message = "Field [metadata] values must be no greater than {max} characters long")
@MapValueNotNull(message = "Field [metadata] must not have null values")
public @interface ValidExternalMetadata {
    String message() default "Invalid metadata";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @Target({FIELD, PARAMETER})
    @Retention(RUNTIME)
    @interface List {
        ValidExternalMetadata[] value();
    }
}
