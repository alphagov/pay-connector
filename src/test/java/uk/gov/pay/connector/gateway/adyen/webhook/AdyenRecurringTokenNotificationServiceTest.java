package uk.gov.pay.connector.gateway.adyen.webhook;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.util.IpDomainMatcher;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdyenRecurringTokenNotificationServiceTest {

    private static final String FORWARDED_IP = "5.6.7.8";
    private static final String NON_ADYEN_IP = "8.8.8.8";
    private static final String HMAC_SIGNATURE = "sha256=test-signature";

    @Mock
    private AdyenGatewayConfig adyenGatewayConfig;

    @Mock
    private IpDomainMatcher ipDomainMatcher;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Mock
    private AdyenNotificationValidator mockAdyenNotificationValidator;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    private AdyenRecurringTokenNotificationService adyenRecurringTokenNotificationService;

    @BeforeEach
    void setUp() {
        adyenRecurringTokenNotificationService = new AdyenRecurringTokenNotificationService(
                adyenGatewayConfig,
                mockAdyenNotificationValidator);
        Logger logger = (Logger) LoggerFactory.getLogger(AdyenRecurringTokenNotificationService.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(mockAppender);
    }

    @Test
    void shouldAcceptValidTokenNotification() {
        when(mockAdyenNotificationValidator.isValidIpAddress(FORWARDED_IP)).thenReturn(true);

        String payload = TestTemplateResourceLoader.load(TestTemplateResourceLoader.ADYEN_TOKEN_NOTIFICATION);

        boolean result = adyenRecurringTokenNotificationService.handleNotificationFor(payload, HMAC_SIGNATURE, FORWARDED_IP);

        assertTrue(result);
        verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents.stream()
                .anyMatch(event -> event.getFormattedMessage().equals("Processed Adyen token notification")), is(true));
    }

    @Test
    void shouldRejectNotificationWhenForwardedIpHeaderIsMissing() {
        boolean result = adyenRecurringTokenNotificationService.handleNotificationFor("{}", HMAC_SIGNATURE, null);

        assertFalse(result);
        verifyNoInteractions(ipDomainMatcher);
    }

    @Test
    void shouldRejectNotificationWhenForwardedIpDoesNotMatchConfiguredDomain() {
        when(mockAdyenNotificationValidator.isValidIpAddress(NON_ADYEN_IP)).thenReturn(false);

        boolean result = adyenRecurringTokenNotificationService.handleNotificationFor("{}", HMAC_SIGNATURE, NON_ADYEN_IP);

        assertFalse(result);
    }
}
