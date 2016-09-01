package uk.gov.pay.connector.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;

public class DnsUtilsITest {

    private DnsUtils dnsUtils;

    @Before
    public void setup() {
        dnsUtils = new DnsUtils();
    }

    @Test
    public void reverseDnsShouldReturnHostIfIpIsValid() throws Exception {
        Assert.assertThat(dnsUtils.reverseDnsLookup("195.35.90.1").isPresent(), is(true));
        Assert.assertThat(dnsUtils.reverseDnsLookup("195.35.90.1").get(), is("hello.worldpay.com."));
    }

    @Test
    public void reverseDnsShouldNotReturnHostIfIpIsNotValid() throws Exception {
        Assert.assertThat(dnsUtils.reverseDnsLookup("123.234.567.890").isPresent(), is(false));
        Assert.assertThat(dnsUtils.reverseDnsLookup("not-an-ip").isPresent(), is(false));
    }

}