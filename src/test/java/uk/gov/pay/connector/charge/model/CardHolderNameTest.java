package uk.gov.pay.connector.charge.model;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class CardHolderNameTest {
    
    @Test
    public void shouldSerialiseValidString() {
        assertThat(CardHolderName.of("bla").toString(), is("bla"));
    }

    @Test
    public void shouldSerialiseValidString_ifNullable() {
        assertThat(CardHolderName.ofNullable("bla bla").toString(), is("bla bla"));
    }

    @Test
    public void shouldReturnNullIfStringIsNull_ifNullable() {
        assertThat(CardHolderName.ofNullable(null), is(nullValue()));
    }
    
    @Test
    public void shouldReturnNullIfStringIsBlank_ifNullable() {
        assertThat(CardHolderName.ofNullable(""), is(nullValue()));
    }
}
