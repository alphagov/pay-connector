package uk.gov.pay.connector.model.domain;


import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;

class EmailCollectionModeTest {

    @Test
    void shouldConvertMandatory() {
        assertThat(EmailCollectionMode.fromString("mandatory"), is(EmailCollectionMode.MANDATORY));
        assertThat(EmailCollectionMode.fromString("MANDATORY"), is(EmailCollectionMode.MANDATORY));
    }
    
    @Test
    void shouldConvertOptional() {
        assertThat(EmailCollectionMode.fromString("optional"), is(EmailCollectionMode.OPTIONAL));
        assertThat(EmailCollectionMode.fromString("OPTIONAL"), is(EmailCollectionMode.OPTIONAL));
    }
    

    @Test
    void shouldConvertOff() {
        assertThat(EmailCollectionMode.fromString("off"), is(EmailCollectionMode.OFF));
        assertThat(EmailCollectionMode.fromString("OFF"), is(EmailCollectionMode.OFF));
    }

    @Test
    void shouldThrowIfConvertingUnknownEnum() {
        assertThrows(IllegalArgumentException.class, ()  -> {
            EmailCollectionMode.fromString("nope");
        });
    }
}
