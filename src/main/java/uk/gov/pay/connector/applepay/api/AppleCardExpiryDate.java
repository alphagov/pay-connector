package uk.gov.pay.connector.applepay.api;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AppleCardExpiryDate {
    private LocalDate date;
    private final static DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    private static LocalDate from(String applicationExpirationDate){
        return LocalDate.parse(applicationExpirationDate, FORMAT);
    }

    public AppleCardExpiryDate(String date) {
        this.date = from(date);
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

        return date != null ? date.equals(that.date) : that.date == null;
    }

    @Override
    public int hashCode() {
        return date != null ? date.hashCode() : 0;
    }
}
