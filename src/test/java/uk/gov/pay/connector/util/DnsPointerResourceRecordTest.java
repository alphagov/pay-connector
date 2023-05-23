package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class DnsPointerResourceRecordTest {
    
    @Test
    void getReverseIpFromIpAddress() {
        assertThat(new DnsPointerResourceRecord("195.35.90.1").getReverseIp(), is("1.90.35.195.in-addr.arpa"));
    }
}
