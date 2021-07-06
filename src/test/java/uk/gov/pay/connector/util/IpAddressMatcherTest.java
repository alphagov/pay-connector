package uk.gov.pay.connector.util;

import org.apache.commons.validator.routines.InetAddressValidator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpAddressMatcherTest {
    private static final Set<String> ALLOWED_IP_ADDRESSES = CidrUtils.getIpAddresses(
            Set.of("9.9.9.9/32", "1.2.3.0/24", "3.6.9.12/32"));

    private IpAddressMatcher ipAddressMatcher = new IpAddressMatcher(new InetAddressValidator());

    @Test
    void shouldReturnTrueWhenOnlyOneForwardedIpAddressMatches() {
        String ipAddress = "9.9.9.9";

        assertTrue(ipAddressMatcher.isMatch(ipAddress, ALLOWED_IP_ADDRESSES));
    }

    @Test
    void shouldReturnFalseWhenOnlyOneForwardedIpAddressDoesNotMatch() {
        String ipAddress = "1.1.1.1";

        assertFalse(ipAddressMatcher.isMatch(ipAddress, ALLOWED_IP_ADDRESSES));
    }

    @Test
    void shouldReturnTrueWhenLastOfForwardedIpAddressesMatches() {
        String ipAddresses = "102.106.2.1, 1.2.3.4";

        assertTrue(ipAddressMatcher.isMatch(ipAddresses, ALLOWED_IP_ADDRESSES));
    }

    @Test
    void shouldReturnFalseWhenLastOfForwardedIpAddressesDoesNotMatch() {
        String ipAddresses = "1.1.1.1, 102.106.2.1";

        assertFalse(ipAddressMatcher.isMatch(ipAddresses, ALLOWED_IP_ADDRESSES));
    }

    @Test
    void shouldReturnFalseWhenForwardedIpAddressIsNull() {
        assertFalse(ipAddressMatcher.isMatch(null, ALLOWED_IP_ADDRESSES));
    }

    @Test
    void shouldReturnFalseWhenForwardedIpAddressIsEmpty() {
        assertFalse(ipAddressMatcher.isMatch("", ALLOWED_IP_ADDRESSES));
    }

    @Test
    void shouldReturnFalseWhenForwardedIpAddressIsInBadFormat() {
        assertFalse(ipAddressMatcher.isMatch("1.2.x.1", ALLOWED_IP_ADDRESSES));
    }

    @Test
    void shouldReturnFalseWhenListOfAllowedIpAddressesIsNull() {
        assertFalse(ipAddressMatcher.isMatch("1.2.3.4, 102.106.2.1", null));
    }

    @Test
    void shouldReturnFalseWhenListOfAllowedIpAddressesIsEmpty() {
        assertFalse(ipAddressMatcher.isMatch("1.2.3.4, 102.106.2.1", Collections.emptySet()));
    }
}
