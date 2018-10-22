package uk.gov.pay.connector.charge.model;

import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;

public class FromDate extends DateParam {
    private FromDate(ZonedDateTime date) {
        super(date);
    }

    private FromDate(String date) {
        super(date);
    }

    public static FromDate of(String date) {
        return new FromDate(date);
    }

    public static FromDate of(ZonedDateTime date) {
        return new FromDate(date);
    }

    public static FromDate ofNullable(String date) {
        if (StringUtils.isBlank(date)) {
            return null;
        }
        return new FromDate(date);
    }
}
