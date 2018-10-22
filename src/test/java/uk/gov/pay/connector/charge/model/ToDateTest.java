package uk.gov.pay.connector.charge.model;

import org.junit.Test;

import java.time.ZonedDateTime;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class ToDateTest {


    @Test
    public void shouldSerialiseValidDate() {
        assertThat(ToDate.of("2012-06-30T12:30:40Z[UTC]").getRawValue(), is(ZonedDateTime.parse("2012-06-30T12:30:40Z[UTC]")));
    }

    @Test
    public void shouldSerialiseValidDate_ifNullable() {
        assertThat(ToDate.ofNullable("2012-06-30T12:30:40Z[UTC]").getRawValue(), is(ZonedDateTime.parse("2012-06-30T12:30:40Z[UTC]")));
    }

    @Test
    public void shouldReturnNullIfDateIsNull_ifNullable() {
        assertThat(ToDate.ofNullable(null), is(nullValue()));
    }
    
    @Test
    public void shouldReturnNullIfDateIsBlank_ifNullable() {
        assertThat(ToDate.ofNullable(""), is(nullValue()));
    }
}
