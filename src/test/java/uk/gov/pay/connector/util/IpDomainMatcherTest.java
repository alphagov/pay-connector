package uk.gov.pay.connector.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpDomainMatcherTest {

    private IpDomainMatcher ipDomainMatcher;
    
    @Mock
    private ReverseDnsLookup reverseDnsLookup;

    @BeforeEach
    void setup() {
        ipDomainMatcher = new IpDomainMatcher(reverseDnsLookup);
    }
    
    @Test
    void reverseDnsShouldReturnHostIfIpIsValid() {
        when(reverseDnsLookup.lookup(new DnsPointerResourceRecord("195.35.90.1"))).thenReturn(Optional.of("worldpay.com."));
        assertThat(ipDomainMatcher.ipMatchesDomain("195.35.90.1", "worldpay.com"), is(true));
    }
    
    @Test
    void reverseDnsShouldCorrectlyMatchValidForwardedHeaderToDomain() {
        when(reverseDnsLookup.lookup(new DnsPointerResourceRecord("195.35.90.1"))).thenReturn(Optional.of("worldpay.com."));
        when(reverseDnsLookup.lookup(new DnsPointerResourceRecord("8.8.8.8"))).thenReturn(Optional.of("dns.google."));
        assertThat(ipDomainMatcher.ipMatchesDomain("8.8.8.8, 195.35.90.1", "worldpay.com"), is(true));
        assertThat(ipDomainMatcher.ipMatchesDomain("8.8.8.8, 8.8.8.8", "worldpay.com"), is(false));
    }

    @Test
    void reverseDnsShouldFailGracefullyIfForwardedHeaderIsNotValid() {
        when(reverseDnsLookup.lookup(any(DnsPointerResourceRecord.class))).thenReturn(Optional.empty());
        assertThat(ipDomainMatcher.ipMatchesDomain("not-an-ip", "worldpay.com"), is(false));
        assertThat(ipDomainMatcher.ipMatchesDomain(null, "worldpay.com"), is(false));
    }
}
