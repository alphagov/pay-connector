package uk.gov.pay.connector.charge.model;

import uk.gov.pay.connector.exception.ValidationException;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

public class DateParam {
    
    private ZonedDateTime date;

    public DateParam(ZonedDateTime date) {
        this.date = date;
    }
    
    public static DateParam of(String date) {
        try {
            ZonedDateTime parsedDate = ZonedDateTime.parse(date);
            return new DateParam(parsedDate);
        } catch (DateTimeParseException exc) {
            throw new ValidationException("DateParam is invalid");
        }
    }

    public static DateParam ofNullable(String date) {
        if (date == null) {
            return null;
        }
        return DateParam.of(date);
    }

    @Override
    public String toString() {
        return date.toString();
    }
}
