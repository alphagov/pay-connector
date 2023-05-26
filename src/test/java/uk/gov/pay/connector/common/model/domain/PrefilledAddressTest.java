package uk.gov.pay.connector.common.model.domain;


import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class PrefilledAddressTest {
    
    @Test
    void shouldSetCountryWhenProvidedValueIsTwoCharactersLong() {
        var address = createPrefilledAdddressWithCountryOf("GB");
        assertThat(address.getCountry(), is("GB"));
    }

    @Test
    void shouldSetCountryToNullWhenCountryNotSupplied() {
        var address = createPrefilledAdddressWithCountryOf(null);
        assertThat(address.getCountry(), is(nullValue()));
    }
    
    @Test
    void shouldSetCountryToNullWhenProvidedCountryTooShort() {
        var address = createPrefilledAdddressWithCountryOf("G");
        assertThat(address.getCountry(), is(nullValue()));
    }

    @Test
    void shouldSetCountryToNullWhenProvidedCountryTooLong() {
        var address = createPrefilledAdddressWithCountryOf("GBR");
        assertThat(address.getCountry(), is(nullValue()));
    }
    
    
    private PrefilledAddress createPrefilledAdddressWithCountryOf(String country) {
        return new PrefilledAddress("line1", "line2", "postcode", "city", "county", country);
    }

}
