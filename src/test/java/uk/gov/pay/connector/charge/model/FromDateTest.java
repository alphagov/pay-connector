package uk.gov.pay.connector.charge.model;

import org.junit.Test;

import java.time.ZonedDateTime;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class FromDateTest {


    @Test
    public void shouldSerialiseValidDate() {
        assertThat(FromDate.of("2012-06-30T12:30:40Z[UTC]").getRawValue(), is(ZonedDateTime.parse("2012-06-30T12:30:40Z[UTC]")));
    }

    @Test
    public void shouldSerialiseValidDate_ifNullable() {
        assertThat(FromDate.ofNullable("2012-06-30T12:30:40Z[UTC]").getRawValue(), is(ZonedDateTime.parse("2012-06-30T12:30:40Z[UTC]")));
    }

    @Test
    public void shouldReturnNullIfDateIsNull_ifNullable() {
        assertThat(FromDate.ofNullable(null), is(nullValue()));
    }
    
    @Test
    public void shouldReturnNullIfDateIsBlank_ifNullable() {
        assertThat(FromDate.ofNullable(""), is(nullValue()));
    }
}
