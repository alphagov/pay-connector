package uk.gov.pay.connector.common.model.domain;


import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class PrefilledAddressTest {
    
    @Test
    void shouldSetCountryWhenProvidedValueIsTwoCharactersLong() {
        var address = createPrefilledAddressWithCountryOf("GB");
        assertThat(address.getCountry(), is("GB"));
    }

    @Test
    void shouldSetCountryToNullWhenCountryNotSupplied() {
        var address = createPrefilledAddressWithCountryOf(null);
        assertThat(address.getCountry(), is(nullValue()));
    }
    
    @Test
    void shouldSetCountryToNullWhenProvidedCountryTooShort() {
        var address = createPrefilledAddressWithCountryOf("G");
        assertThat(address.getCountry(), is(nullValue()));
    }

    @Test
    void shouldSetCountryToNullWhenProvidedCountryTooLong() {
        var address = createPrefilledAddressWithCountryOf("GBR");
        assertThat(address.getCountry(), is(nullValue()));
    }
    
    
    private PrefilledAddress createPrefilledAddressWithCountryOf(String country) {
        return new PrefilledAddress("line1", "line2", "postcode", "city", "county", country);
    }

}
