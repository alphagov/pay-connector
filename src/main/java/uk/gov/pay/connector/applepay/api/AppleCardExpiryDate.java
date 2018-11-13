package uk.gov.pay.connector.applepay.api;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class AppleCardExpiryDate {
    private final LocalDate date;
    private final static DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    public AppleCardExpiryDate(String date) {
        Objects.requireNonNull(date);
        this.date = LocalDate.parse(date, FORMAT);
    }

    public LocalDate getDate() {
        return date;
    }

    @Override
    public String toString() {
        return FORMAT.format(date);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AppleCardExpiryDate that = (AppleCardExpiryDate) o;

        return date.equals(that.date);
    }

    @Override
    public int hashCode() {
        return date.hashCode();
    }
}
