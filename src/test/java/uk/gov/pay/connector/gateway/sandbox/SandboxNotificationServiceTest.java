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
    private static final String AUTH_TOKEN = "let-me-in";

    private SandboxNotificationService notificationService = new SandboxNotificationService(
            new IpAddressMatcher(new InetAddressValidator()),
            ALLOWED_IP_ADDRESSES,
            AUTH_TOKEN);

    @Test
    void shouldReturnTrueWhenForwardedIpAddressIsInAllowedIpAddresses() {
        assertTrue(notificationService.handleNotificationFor("102.108.0.6, 3.3.3.1", null));
    }

    @Test
    void shouldReturnTrueWhenValidAuthTokenIsProvided() {
        assertTrue(notificationService.handleNotificationFor("99.99.99.99", AUTH_TOKEN));
    }

    @Test
    void shouldReturnFalseWhenForwardedIpAddressIsNotInAllowedIpAddresses() {
        assertFalse(notificationService.handleNotificationFor("102.108.0.6, 2.2.2.2", "not-the-correct-value"));
    }
}
