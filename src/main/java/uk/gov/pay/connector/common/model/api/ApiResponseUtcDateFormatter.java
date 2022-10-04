package uk.gov.pay.connector.common.model.api;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class ApiResponseUtcDateFormatter {

    /**
     * DateTimeFormatter that produces a standard ISO-8601 local date in UTC
     * without an offset, for example 2022-10-04
     */
    public static final DateTimeFormatter ISO_LOCAL_DATE_IN_UTC = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

}
