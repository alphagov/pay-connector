package uk.gov.pay.connector.charge.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class FirstDigitsCardNumber {

    private final String firstDigitsCardNumber;

    private FirstDigitsCardNumber(String firstDigitsCardNumber) {
        this.firstDigitsCardNumber = Objects.requireNonNull(firstDigitsCardNumber);
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

    @Override
    public boolean equals(Object other) {
        if (other != null && other.getClass() == FirstDigitsCardNumber.class) {
            FirstDigitsCardNumber that = (FirstDigitsCardNumber) other;
            return this.firstDigitsCardNumber.equals(that.firstDigitsCardNumber);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return firstDigitsCardNumber.hashCode();
    }

    @Override
    public String toString() {
        return firstDigitsCardNumber;
    }

}
