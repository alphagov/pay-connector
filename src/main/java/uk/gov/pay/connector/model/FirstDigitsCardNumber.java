package uk.gov.pay.connector.model;

import java.util.Objects;

public class FirstDigitsCardNumber {

    private final String firstDigitsCardNumber;

    private FirstDigitsCardNumber(String firstDigitsCardNumber) {
        this.firstDigitsCardNumber = Objects.requireNonNull(firstDigitsCardNumber);
    }

    public static FirstDigitsCardNumber of(String firstDigitsCardNumber) {
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
