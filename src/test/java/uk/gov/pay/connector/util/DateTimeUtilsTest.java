package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateTimeUtilsTest {

    @Test
    void shouldConvertUTCZonedISO_8601StringToADateTime() {
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
    void shouldConvertNonUTCZonedISO_8601StringToADateTime() {
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
    void shouldConvertAn_UTCOffsetDateStringToADateTime() {
        String aDate = "2010-01-01T12:10:10+01:00";
        Optional<ZonedDateTime> result = DateTimeUtils.toUTCZonedDateTime(aDate);
        assertTrue(result.isPresent());
        assertThat(result.get().toString(), endsWith("Z"));
        assertThat(result.get().toString(), is("2010-01-01T11:10:10Z"));
    }

    @Test
    void toUserFriendlyDateShouldFormatAsDateInLondonTimezone() {
        Instant instant = Instant.parse("2016-07-07T23:24:48Z");

        String result = DateTimeUtils.toUserFriendlyDate(instant);

        assertThat(result, is("8 July 2016 - 00:24:48"));
    }

    @Test
    void toDateFormatShouldNotProduceDateWithOffset() {
        ZonedDateTime now1 = ZonedDateTime.of(LocalDate.of(2016, 12, 23), LocalTime.of(12, 34), ZoneOffset.UTC);
        String output1 = DateTimeUtils.toUTCDateString(now1);
        assertThat(output1, is("2016-12-23"));

        ZonedDateTime now2 = ZonedDateTime.of(LocalDate.of(2016, 6, 19), LocalTime.of(0, 22), ZoneId.of("Europe/London"));
        String output2 = DateTimeUtils.toUTCDateString(now2);
        assertThat(output2, is("2016-06-18"));
    }

    @Test
    void shouldConvertUnixEpochTimeToUTCZonedDateTime() {
        ZonedDateTime zonedDateTime1 = DateTimeUtils.toUTCZonedDateTime(1L);
        assertThat(zonedDateTime1.toString(), is("1970-01-01T00:00:01Z"));

        ZonedDateTime zonedDateTime2 = DateTimeUtils.toUTCZonedDateTime(1589395533L);
        assertThat(zonedDateTime2.toString(), is("2020-05-13T18:45:33Z"));
    }

    @Test
    void shouldReturnNullIfUnixEpochTimeIsNull() {
        Long epochTime = null;
        ZonedDateTime zonedDateTime = DateTimeUtils.toUTCZonedDateTime(epochTime);

        assertThat(zonedDateTime, is(nullValue()));
    }
}
