package uk.gov.pay.connector.gateway.adyen.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.util.IpDomainMatcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdyenNotificationServiceTest {

    private AdyenNotificationService adyenNotificationService;

    @Mock
    private AdyenGatewayConfig adyenGatewayConfig;

    @Mock
    private IpDomainMatcher ipDomainMatcher;

    @BeforeEach
    void setUp() {
        adyenNotificationService = new AdyenNotificationService(adyenGatewayConfig, ipDomainMatcher);
    }

    @Test
    void shouldAcceptNotificationWhenForwardedIpMatchesConfiguredDomain() {
        when(adyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
        when(ipDomainMatcher.ipMatchesDomain("5.6.7.8", "out.adyen.com.")).thenReturn(true);

        boolean result = adyenNotificationService.handleNotificationFor("{\"notificationItems\":[]}", "5.6.7.8");

        assertTrue(result);
    }

    @Test
    void shouldRejectNotificationWhenForwardedIpHeaderIsMissing() {
        boolean result = adyenNotificationService.handleNotificationFor("{\"notificationItems\":[]}", null);

        assertFalse(result);
        verifyNoInteractions(ipDomainMatcher);
    }
    

    @Test
    void shouldRejectNotificationWhenForwardedIpDoesNotMatchConfiguredDomain() {
        when(adyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
        when(ipDomainMatcher.ipMatchesDomain("8.8.8.8", "out.adyen.com.")).thenReturn(false);

        boolean result = adyenNotificationService.handleNotificationFor("{\"notificationItems\":[]}", "8.8.8.8");

        assertFalse(result);
    }
}

