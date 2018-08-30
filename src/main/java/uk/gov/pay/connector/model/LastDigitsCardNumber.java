package uk.gov.pay.connector.model;

import java.util.Objects;

public class LastDigitsCardNumber {

    private final String lastDigitsCardNumber;

    private LastDigitsCardNumber(String lastDigitsCardNumber) {
        this.lastDigitsCardNumber = Objects.requireNonNull(lastDigitsCardNumber);
    }

    public static LastDigitsCardNumber of(String lastDigitsCardNumber) {
        return new LastDigitsCardNumber(lastDigitsCardNumber);
    }

    @Override
    public boolean equals(Object other) {
        if (other != null && other.getClass() == LastDigitsCardNumber.class) {
            LastDigitsCardNumber that = (LastDigitsCardNumber) other;
            return this.lastDigitsCardNumber.equals(that.lastDigitsCardNumber);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return lastDigitsCardNumber.hashCode();
    }

    @Override
    public String toString() {
        return String.valueOf(lastDigitsCardNumber);
    }

}
