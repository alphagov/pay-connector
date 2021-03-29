package uk.gov.pay.connector.charge.model;

import org.apache.commons.lang3.StringUtils;
import uk.gov.service.payments.commons.model.WrappedStringValue;

import java.util.Objects;

public class FirstDigitsCardNumber extends WrappedStringValue {

    private FirstDigitsCardNumber(String firstDigitsCardNumber) {
        super(firstDigitsCardNumber);
    }

    private static boolean isValid(String firstDigitsCardNumber) {
        return firstDigitsCardNumber != null && firstDigitsCardNumber.length() == 6 && StringUtils.isNumeric(firstDigitsCardNumber);
    }

    public static FirstDigitsCardNumber of(String firstDigitsCardNumber) {
        if (!(isValid(firstDigitsCardNumber))) {
            throw new RuntimeException("Expecting 6 first digits of card number");
        }
        return new FirstDigitsCardNumber(firstDigitsCardNumber);
    }

    public static FirstDigitsCardNumber ofNullable(String firstDigitsCardNumber) {
        if (!(isValid(firstDigitsCardNumber))) {
            return null;
        }
        return new FirstDigitsCardNumber(firstDigitsCardNumber);
    }

}
