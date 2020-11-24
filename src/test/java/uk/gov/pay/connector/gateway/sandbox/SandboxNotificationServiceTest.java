package uk.gov.pay.connector.gateway.sandbox;

import org.apache.commons.validator.routines.InetAddressValidator;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.util.CidrUtils;
import uk.gov.pay.connector.util.IpAddressMatcher;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxNotificationServiceTest {
    private static final Set<String> ALLOWED_IP_ADDRESSES = CidrUtils.getIpAddresses(Set.of("3.3.3.0/31", "9.9.9.9/32"));

    private SandboxNotificationService notificationService = new SandboxNotificationService(
            new IpAddressMatcher(new InetAddressValidator()),
            ALLOWED_IP_ADDRESSES);

    @Test
    void shouldReturnTrueWhenForwardedIpAddressIsInAllowedIpAddresses() {
        assertTrue(notificationService.handleNotificationFor("3.3.3.1, 102.108.0.6"));
    }

    @Test
    void shouldReturnFalseWhenForwardedIpAddressIsNotInAllowedIpAddresses() {
        assertFalse(notificationService.handleNotificationFor("2.2.2.2, 102.108.0.6"));
    }
}
