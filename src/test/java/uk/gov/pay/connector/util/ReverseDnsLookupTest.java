package uk.gov.pay.connector.util;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ReverseDnsLookupTest {
    
    private ReverseDnsLookup reverseDnsLookup = new ReverseDnsLookup();

    @Test
    public void reverseDnsShouldReturnHostIfIpIsValid() {
        var pointerRecord = new DnsPointerResourceRecord("195.35.90.1");
        assertThat(reverseDnsLookup.lookup(pointerRecord).isPresent(), is(true));
        assertThat(reverseDnsLookup.lookup(pointerRecord).get(), is("hello.worldpay.com."));
    }

    @Test
    public void reverseDnsShouldNotReturnHostIfIpIsNotValid() {
        assertThat(reverseDnsLookup.lookup(new DnsPointerResourceRecord("123.234.567.890")).isPresent(), is(false));
        assertThat(reverseDnsLookup.lookup(new DnsPointerResourceRecord("not-an-ip")).isPresent(), is(false));
    }
}
