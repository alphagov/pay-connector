package uk.gov.pay.connector.util;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.lang.String.join;

/**
 * Class that represents a DNS Pointer (PTR) resource record. 
 */
public class DnsPointerResourceRecord {
    
    private final String reverseIp;

    public DnsPointerResourceRecord(String ipAddress) {
        List<String> components = Arrays.asList(ipAddress.split("\\."));
        Collections.reverse(components);
        this.reverseIp = join(".", components) + ".in-addr.arpa";
    }

    public String getReverseIp() {
        return reverseIp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        DnsPointerResourceRecord that = (DnsPointerResourceRecord) o;

        return new EqualsBuilder()
                .append(reverseIp, that.reverseIp)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(reverseIp)
                .toHashCode();
    }
}
