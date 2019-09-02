package uk.gov.pay.connector.common.model.domain;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class PrefilledAddressTest {
    
    @Test
    public void shouldSetCountryWhenProvidedValueIsTwoCharactersLong() {
        var address = createPrefilledAdddressWithCountryOf("GB");
        assertThat(address.getCountry(), is("GB"));
    }

    @Test
    public void shouldSetCountryToNullWhenCountryNotSupplied() {
        var address = createPrefilledAdddressWithCountryOf(null);
        assertThat(address.getCountry(), is(nullValue()));
    }
    
    @Test
    public void shouldSetCountryToNullWhenProvidedCountryTooShort() {
        var address = createPrefilledAdddressWithCountryOf("G");
        assertThat(address.getCountry(), is(nullValue()));
    }

    @Test
    public void shouldSetCountryToNullWhenProvidedCountryTooLong() {
        var address = createPrefilledAdddressWithCountryOf("GBR");
        assertThat(address.getCountry(), is(nullValue()));
    }
    
    
    private PrefilledAddress createPrefilledAdddressWithCountryOf(String country) {
        return new PrefilledAddress("line1", "line2", "postcode", "city", "county", country);
    }

}
