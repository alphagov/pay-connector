package uk.gov.pay.connector.util;

import org.apache.commons.validator.routines.InetAddressValidator;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class DnsUtilsITest {

    private DnsUtils dnsUtils;

    @Before
    public void setup() {
        dnsUtils = new DnsUtils();
    }

    @Test
    public void shouldReturnAnIpAddressForAValidHostname() {
        InetAddressValidator validator = InetAddressValidator.getInstance();
        Optional<String> maybeIp = dnsUtils.dnsLookup("build.ci.pymnt.uk");
        assertThat(maybeIp.isPresent(), is(true));
        assertThat(validator.isValidInet4Address(maybeIp.get()), is (true));
    }

    @Test
    public void reverseDnsShouldCorrectlyMatchValidForwardedHeaderToDomain() {
        assertThat(dnsUtils.ipMatchesDomain("195.35.90.1, 8.8.8.8", "worldpay.com"), is(true));
        assertThat(dnsUtils.ipMatchesDomain("8.8.8.8, 8.8.8.8", "worldpay.com"), is(false));
    }

    @Test
    public void reverseDnsShouldFailGracefullyIfForwardedHeaderIsNotValid() {
        assertThat(dnsUtils.ipMatchesDomain("not-an-ip", "worldpay.com"), is(false));
        assertThat(dnsUtils.ipMatchesDomain(null, "worldpay.com"), is(false));
    }

    @Test
    public void reverseDnsShouldReturnHostIfIpIsValid() {
        assertThat(dnsUtils.reverseDnsLookup("195.35.90.1").isPresent(), is(true));
        assertThat(dnsUtils.reverseDnsLookup("195.35.90.1").get(), is("hello.worldpay.com."));
    }

    @Test
    public void shouldNotReturnAnIpAddressForAnInvalidHostname() {
        assertThat(dnsUtils.dnsLookup("asdasd").isPresent(), is(false));
    }

    @Test
    public void reverseDnsShouldNotReturnHostIfIpIsNotValid() {
        assertThat(dnsUtils.reverseDnsLookup("123.234.567.890").isPresent(), is(false));
        assertThat(dnsUtils.reverseDnsLookup("not-an-ip").isPresent(), is(false));
    }

}
