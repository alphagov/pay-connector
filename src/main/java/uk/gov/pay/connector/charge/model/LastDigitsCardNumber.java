package uk.gov.pay.connector.charge.model;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.commons.model.WrappedStringValue;

public class LastDigitsCardNumber extends WrappedStringValue {

    private LastDigitsCardNumber(String lastDigitsCardNumber) {
        super(lastDigitsCardNumber);
    }

    private static boolean isValid(String lastDigitsCardNumber) {
        return lastDigitsCardNumber != null && lastDigitsCardNumber.length() == 4 && StringUtils.isNumeric(lastDigitsCardNumber);
    }

    public static LastDigitsCardNumber of(String lastDigitsCardNumber) {
        if (!(isValid(lastDigitsCardNumber))) {
            throw new RuntimeException("Expecting 4 last digits of card number");
        }
        return new LastDigitsCardNumber(lastDigitsCardNumber);
    }

    public static LastDigitsCardNumber ofNullable(String lastDigitsCardNumber) {
        if (!(isValid(lastDigitsCardNumber))) {
            return null;
        }
        return new LastDigitsCardNumber(lastDigitsCardNumber);
    }

}
