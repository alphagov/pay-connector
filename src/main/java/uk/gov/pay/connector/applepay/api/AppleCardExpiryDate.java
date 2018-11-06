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
}
