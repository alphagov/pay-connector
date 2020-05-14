package uk.gov.pay.connector.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import static java.time.Instant.ofEpochSecond;
import static java.time.ZonedDateTime.ofInstant;
import static java.util.Objects.isNull;

public class DateTimeUtils {

    private static final ZoneId UTC = ZoneId.of("Z");
    private static DateTimeFormatter dateTimeFormatterAny = DateTimeFormatter.ISO_ZONED_DATE_TIME;
    private static DateTimeFormatter localDateFormatter = DateTimeFormatter.ISO_DATE;

    /**
     * Converts any valid ZonedDateTime String (ISO_8601) representation to a UTC ZonedDateTime
     * <p>
     * e.g. <br/>
     * 1. 2010-01-01T12:00:00+01:00[Europe/Paris] ==>  ZonedDateTime("2010-12-31T22:59:59.132Z") <br/>
     * 2. 2010-12-31T22:59:59.132Z ==>  ZonedDateTime("2010-12-31T22:59:59.132Z") <br/>
     * </p>
     *
     * @param dateString e.g.
     * @return @ZonedDateTime instance represented by dateString in UTC ("Z") or
     * @Optional.empty() if the dateString does not represent a valid ISO_8601 zone string
     * @see "https://docs.oracle.com/javase/8/docs/api/java/time/ZonedDateTime.html"
     */
    public static Optional<ZonedDateTime> toUTCZonedDateTime(String dateString) {
        try {
            ZonedDateTime utcDateTime = ZonedDateTime
                    .parse(dateString, dateTimeFormatterAny)
                    .withZoneSameInstant(UTC);
            return Optional.of(utcDateTime);
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    /**
     * Converts Epoch time (number of seconds that have elapsed since Unix epoch time which is 00:00:00 UTC on 1 January 1970)
     * to a UTC ZonedDateTime
     * <p>
     * e.g. <br/>
     * 1) 1 ==>  ZonedDateTime("1970-01-01T00:00:01Z"). 1 Second from Unix epoch time<br/>
     * 2) 1000 ==>  ZonedDateTime("1970-01-01T00:16:40Z"). 1000 seconds from Unix epoch time<br/>
     * </p>
     * @param epochSecond
     * @return @ZonedDateTime instance represented by dateString in UTC ("Z") or null if `epochSecond` is null
     */
    public static ZonedDateTime toUTCZonedDateTime(Long epochSecond) {
        if (isNull(epochSecond)) {
            return null;
        } else {
            Instant instant = ofEpochSecond(epochSecond);
            return ofInstant(instant, UTC);
        }
    }

    /**
     * Converts a LocalDateTime to a UTC ISO_8601 string representation
     * <p>
     * e.g. <br/>
     * 1. LocalDateTime("2010-01-01") ==> "2010-12-01" <br/>
     * 2. LocalDateTime("2010-12-31") ==> "2010-12-31" <br/>
     * </p>
     *
     * @param zonedDateTime
     * @return UTC ISO_8601 date string
     */
    public static String toUTCDateString(ZonedDateTime zonedDateTime) {
        return zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDate().format(localDateFormatter);
    }


    public static String toUserFriendlyDate(ZonedDateTime zonedDateTime) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy - HH:mm:ss");

        LocalDateTime localDateTime = zonedDateTime.withZoneSameInstant(ZoneId.of("Europe/London")).toLocalDateTime();

        return localDateTime.format(dateTimeFormatter);
    }
}
