package uk.gov.pay.connector.charge.model;

import uk.gov.service.payments.commons.model.WrappedStringValue;

import java.util.Objects;
import java.util.regex.Pattern;

public class FirstDigitsCardNumber extends WrappedStringValue {

    private static final Pattern SIX_DIGITS = Pattern.compile("[0-9]{6}");

    private FirstDigitsCardNumber(String firstDigitsCardNumber) {
        super(firstDigitsCardNumber);
    }

    public static FirstDigitsCardNumber of(String firstDigitsCardNumber) {
        Objects.requireNonNull(firstDigitsCardNumber, "firstDigitsCardNumber");

        if (!SIX_DIGITS.matcher(firstDigitsCardNumber).matches()) {
            throw new IllegalArgumentException("Expecting 6 first digits of card number");
        }

        return new FirstDigitsCardNumber(firstDigitsCardNumber);
    }

}
