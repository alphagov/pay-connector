package uk.gov.pay.connector.charge.validation;

import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.DomainValidator.ArrayType;
import org.apache.commons.validator.routines.UrlValidator;

import java.util.List;

import static org.apache.commons.validator.routines.UrlValidator.ALLOW_2_SLASHES;
import static org.apache.commons.validator.routines.UrlValidator.ALLOW_LOCAL_URLS;

public class ReturnUrlValidator {
    private static final UrlValidator urlValidator = new UrlValidator(
            new String[]{"http", "https"},
            null,
            ALLOW_LOCAL_URLS + ALLOW_2_SLASHES,
            domainValidator()
    );

    private ReturnUrlValidator() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isValid(String value) {
        return urlValidator.isValid(value);
    }

    private static DomainValidator domainValidator() {
        String[] otherValidTlds = new String[]{"internal", "local"};
        DomainValidator.Item item = new DomainValidator.Item(ArrayType.GENERIC_PLUS, otherValidTlds);
        return DomainValidator.getInstance(true, List.of(item));
    }
}
