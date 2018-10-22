package uk.gov.pay.connector.charge.model;

import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class ToDate extends DateParam {

    private ToDate(ZonedDateTime date) {
        super(date);
    }

    private ToDate(String date) {
        super(date);
    }

    public static ToDate of(String date) {
        return new ToDate(date);
    }

    public static ToDate of(ZonedDateTime date) {
        return new ToDate(date);
    }

    public static ToDate ofNullable(String date) {
        if (isBlank(date)) {
            return null;
        }
        return new ToDate(date);
    }
}
