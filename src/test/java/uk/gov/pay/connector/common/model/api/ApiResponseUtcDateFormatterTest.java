package uk.gov.pay.connector.common.model.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.common.model.api.ApiResponseUtcDateFormatter.ISO_LOCAL_DATE_IN_UTC;

class ApiResponseUtcDateFormatterTest {

    @Test
    void shouldConvertInstantToIsoLocalDateInUtc() {
        var instant = Instant.parse("2022-10-04T12:34:56.789Z");

        String result = ISO_LOCAL_DATE_IN_UTC.format(instant);

        assertThat(result, is("2022-10-04"));
    }

    @Test
    void shouldConvertZonedDateTimeInUtcToIsoLocalDateInUtc() {
        var zonedDateTime = ZonedDateTime.parse("2022-10-04T12:34:56.789Z");

        String result = ISO_LOCAL_DATE_IN_UTC.format(zonedDateTime);

        assertThat(result, is("2022-10-04"));
    }

    @Test
    void shouldConvertZonedDateTimeInAnotherTimeZoneWhereItIsAlreadyTomorrowToIsoLocalDateInUtc() {
        var zonedDateTime = ZonedDateTime.of(LocalDateTime.parse("2022-10-05T06:07:08.123"), ZoneId.of("Pacific/Auckland"));

        String result = ISO_LOCAL_DATE_IN_UTC.format(zonedDateTime);

        assertThat(result, is("2022-10-04"));
    }

}
