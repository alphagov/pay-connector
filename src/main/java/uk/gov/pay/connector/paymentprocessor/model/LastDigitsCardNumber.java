package uk.gov.pay.connector.paymentprocessor.model;

import uk.gov.service.payments.commons.model.WrappedStringValue;

import java.util.Objects;
import java.util.regex.Pattern;

public class LastDigitsCardNumber extends WrappedStringValue {

    private static final Pattern FOUR_DIGITS = Pattern.compile("[0-9]{4}");

    private LastDigitsCardNumber(String lastDigitsCardNumber) {
        super(lastDigitsCardNumber);
    }

    public static LastDigitsCardNumber of(String lastDigitsCardNumber) {
        Objects.requireNonNull(lastDigitsCardNumber, "lastDigitsCardNumber");

        if (!FOUR_DIGITS.matcher(lastDigitsCardNumber).matches()) {
            throw new IllegalArgumentException("Expecting 4 last digits of card number");
        }

        return new LastDigitsCardNumber(lastDigitsCardNumber);
    }

}
