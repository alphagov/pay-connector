package uk.gov.pay.connector.charge.model;

import uk.gov.pay.connector.exception.ValidationException;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

public abstract class DateParam {
    
    private ZonedDateTime date;

    DateParam(ZonedDateTime date) {
        this.date = date;
    }
    
    DateParam(String date) {
        try {
            this.date = ZonedDateTime.parse(date);
        } catch (DateTimeParseException exc) {
            throw new ValidationException("Date is invalid");
        }
    }

    public ZonedDateTime getRawValue() {
        return date;
    }

    @Override
    public String toString() {
        return date.toString();
    }
}
