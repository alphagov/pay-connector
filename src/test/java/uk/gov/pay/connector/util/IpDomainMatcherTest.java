package uk.gov.pay.connector.util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class IpDomainMatcherTest {

    private IpDomainMatcher ipDomainMatcher;
    
    @Mock
    private ReverseDnsLookup reverseDnsLookup;

    @Before
    public void setup() {
        ipDomainMatcher = new IpDomainMatcher(reverseDnsLookup);
    }
    
    @Test
    public void reverseDnsShouldReturnHostIfIpIsValid() {
        when(reverseDnsLookup.lookup("195.35.90.1")).thenReturn(Optional.of("worldpay.com."));
        assertThat(ipDomainMatcher.ipMatchesDomain("195.35.90.1", "worldpay.com"), is(true));
    }
    
    @Test
    public void reverseDnsShouldCorrectlyMatchValidForwardedHeaderToDomain() {
        when(reverseDnsLookup.lookup("195.35.90.1")).thenReturn(Optional.of("worldpay.com."));
        when(reverseDnsLookup.lookup("8.8.8.8")).thenReturn(Optional.of("dns.google."));
        assertThat(ipDomainMatcher.ipMatchesDomain("195.35.90.1, 8.8.8.8", "worldpay.com"), is(true));
        assertThat(ipDomainMatcher.ipMatchesDomain("8.8.8.8, 8.8.8.8", "worldpay.com"), is(false));
    }

    @Test
    public void reverseDnsShouldFailGracefullyIfForwardedHeaderIsNotValid() {
        when(reverseDnsLookup.lookup("not-an-ip")).thenReturn(Optional.empty());
        assertThat(ipDomainMatcher.ipMatchesDomain("not-an-ip", "worldpay.com"), is(false));
        assertThat(ipDomainMatcher.ipMatchesDomain(null, "worldpay.com"), is(false));
    }
}
