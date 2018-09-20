package uk.gov.pay.connector.model;

import org.apache.commons.lang3.StringUtils;

public class LastDigitsCardNumber {

    private final String lastDigitsCardNumber;

    private LastDigitsCardNumber(String lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
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
        return lastDigitsCardNumber;
    }

}
