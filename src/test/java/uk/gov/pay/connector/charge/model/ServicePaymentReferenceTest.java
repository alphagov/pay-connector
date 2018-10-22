package uk.gov.pay.connector.charge.model;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.*;

public class ServicePaymentReferenceTest {
    @Test
    public void shouldSerialiseValidString() {
        assertThat(ServicePaymentReference.of("bla").toString(), is("bla"));
    }

    @Test
    public void shouldSerialiseValidString_ifNullable() {
        assertThat(ServicePaymentReference.ofNullable("bla bla").toString(), is("bla bla"));
    }

    @Test
    public void shouldReturnNullIfStringIsNull_ifNullable() {
        assertThat(ServicePaymentReference.ofNullable(null), is(nullValue()));
    }

    @Test
    public void shouldReturnNullIfStringIsBlank_ifNullable() {
        assertThat(ServicePaymentReference.ofNullable(""), is(nullValue()));
    }
}
