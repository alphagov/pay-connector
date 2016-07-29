package uk.gov.pay.connector.util;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DateTimeUtilsTest {

    @Test
    public void shouldConvertUTCZonedDateTimeToAISO_8601_UTCString() throws Exception {
        ZonedDateTime localDateTime = ZonedDateTime.of(2010, 11, 13, 12, 0, 0, 0, ZoneId.of("Z"));

        String dateString = DateTimeUtils.toUTCDateString(localDateTime);
        assertThat(dateString, is("2010-11-13T12:00:00Z"));
    }

    @Test
    public void shouldConvertNonUTCZonedDateTimeToAISO_8601_UTCString() throws Exception {
        ZonedDateTime localDateTime = ZonedDateTime.of(2010, 11, 13, 12, 0, 0, 0, ZoneId.of("Europe/Paris"));

        String dateString = DateTimeUtils.toUTCDateString(localDateTime);
        assertThat(dateString, is("2010-11-13T11:00:00Z"));
    }

    @Test
    public void shouldConvertUTCZonedISO_8601StringToADateTime() throws Exception {
        String aDate = "2010-01-01T12:00:00Z";
        Optional<ZonedDateTime> result = DateTimeUtils.toUTCZonedDateTime(aDate);
        assertTrue(result.isPresent());
        assertThat(result.get().toString(), endsWith("Z"));

        aDate = "2010-12-31T23:59:59.132Z";
        result = DateTimeUtils.toUTCZonedDateTime(aDate);
        assertTrue(result.isPresent());
        assertThat(result.get().toString(), endsWith("Z"));
    }

    @Test
    public void shouldConvertNonUTCZonedISO_8601StringToADateTime() throws Exception {
        String aDate = "2010-01-01T12:00:00+01:00[Europe/Paris]";
        Optional<ZonedDateTime> result = DateTimeUtils.toUTCZonedDateTime(aDate);
        assertTrue(result.isPresent());
        assertThat(result.get().toString(), endsWith("Z"));
        assertThat(result.get().toString(), is("2010-01-01T11:00Z"));

        aDate = "2010-12-31T23:59:59.132+01:00[Europe/Paris]";
        result = DateTimeUtils.toUTCZonedDateTime(aDate);
        assertTrue(result.isPresent());
        assertThat(result.get().toString(), endsWith("Z"));
        assertThat(result.get().toString(), is("2010-12-31T22:59:59.132Z"));
    }

    @Test
    public void shouldConvertAn_UTCOffsetDateStringToADateTime() throws Exception {
        String aDate = "2010-01-01T12:10:10+01:00";
        Optional<ZonedDateTime> result = DateTimeUtils.toUTCZonedDateTime(aDate);
        assertTrue(result.isPresent());
        assertThat(result.get().toString(), endsWith("Z"));
        assertThat(result.get().toString(), is("2010-01-01T11:10:10Z"));
    }

    @Test
    public void shouldConvertZoneDateTimeToLondonTimeZone() {
        String aDate = "2016-07-07T17:24:48Z";
        Optional<ZonedDateTime> zonedDateTime = DateTimeUtils.toUTCZonedDateTime(aDate);

        String result = DateTimeUtils.toLondonZone(zonedDateTime.get());

        assertThat(result, is("2016-07-07 18:24:48"));

    }

    @Test
    public void toUserFriendlyDateShouldFormatAsDateInLondonTimezone() {
        String aDate = "2016-07-07T23:24:48Z";
        Optional<ZonedDateTime> zonedDateTime = DateTimeUtils.toUTCZonedDateTime(aDate);

        String result = DateTimeUtils.toUserFriendlyDate(zonedDateTime.get());

        assertThat(result, is("8 July 2016 - 00:24:48"));
    }
}
