package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpAddressMatcherTest {
    private static final List<String> ALLOWED_IP_ADDRESSES = List.of("9.9.9.9", "1.2.3.4", "3.6.9.12");
    private IpAddressMatcher ipAddressMatcher = new IpAddressMatcher();

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
    void shouldReturnTrueWhenFirstOfForwardedIpAddressesMatches() {
        String ipAddresses = "1.2.3.4, 102.106.2.1";

        assertTrue(ipAddressMatcher.isMatch(ipAddresses, ALLOWED_IP_ADDRESSES));
    }

    @Test
    void shouldReturnFalseWhenFirstOfForwardedIpAddressesDoesNotMatch() {
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
    void shouldReturnFalseWhenListOfAllowedIpAddressesIsNull() {
        assertFalse(ipAddressMatcher.isMatch("1.2.3.4, 102.106.2.1", null));
    }

    @Test
    void shouldReturnFalseWhenListOfAllowedIpAddressesIsEmpty() {
        assertFalse(ipAddressMatcher.isMatch("1.2.3.4, 102.106.2.1", List.of()));
    }
}
