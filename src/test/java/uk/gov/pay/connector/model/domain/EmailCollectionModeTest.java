package uk.gov.pay.connector.model.domain;

import org.junit.Test;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class EmailCollectionModeTest {

    @Test
    public void shouldConvertMandatory() {
        assertThat(EmailCollectionMode.fromString("mandatory"), is(EmailCollectionMode.MANDATORY));
        assertThat(EmailCollectionMode.fromString("MANDATORY"), is(EmailCollectionMode.MANDATORY));
    }
    
    @Test
    public void shouldConvertOptional() {
        assertThat(EmailCollectionMode.fromString("optional"), is(EmailCollectionMode.OPTIONAL));
        assertThat(EmailCollectionMode.fromString("OPTIONAL"), is(EmailCollectionMode.OPTIONAL));
    }
    

    @Test
    public void shouldConvertOff() {
        assertThat(EmailCollectionMode.fromString("off"), is(EmailCollectionMode.OFF));
        assertThat(EmailCollectionMode.fromString("OFF"), is(EmailCollectionMode.OFF));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfConvertingUnknownEnum() {
        EmailCollectionMode.fromString("nope");
    }
}
