package uk.gov.pay.connector.gateway.adyen.webhook;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.util.IpDomainMatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdyenNotificationValidatorTest {
    
    private AdyenNotificationValidator adyenNotificationValidator;

    @Mock
    private IpDomainMatcher ipDomainMatcher;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventCaptor;

    @Mock
    private AdyenGatewayConfig gatewayConfig;

    public static final String NOTIFICATION_DOMAIN = "notification.adyen.com";

    @BeforeEach
    void setUp() {
        when(gatewayConfig.getNotificationDomain()).thenReturn(NOTIFICATION_DOMAIN);
        adyenNotificationValidator = new AdyenNotificationValidator(gatewayConfig, ipDomainMatcher);

        Logger logger = (Logger) LoggerFactory.getLogger(AdyenNotificationValidator.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(mockAppender);
    }

    private static final String FORWARDED_IP = "192.168.1.1";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldReturnFalseWhenForwardedIpAddressesIsBlankOrNull(String forwardedIpAddresses) {
        assertFalse(adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldLogMissingXForwardedForHeaderWhenForwardedIpAddressesIsBlankOrNull(String forwardedIpAddresses) {
        adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses);

        verify(mockAppender, times(1)).doAppend(loggingEventCaptor.capture());
        LoggingEvent loggingEvent = loggingEventCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.INFO));
        assertThat(loggingEvent.getMessage(),
                is("Adyen notification missing X-Forwarded-For header"));
    }

    @Test
    void shouldReturnTrueWhenIpMatchesDomain() {
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP,NOTIFICATION_DOMAIN)).thenReturn(true);

        assertTrue(adyenNotificationValidator.isValidIpAddress(FORWARDED_IP));
        verify(ipDomainMatcher).ipMatchesDomain(FORWARDED_IP, NOTIFICATION_DOMAIN);
    }

    @Test
    void shouldReturnFalseWhenIpDoesNotMatchDomain() {
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, NOTIFICATION_DOMAIN))
                .thenReturn(false);

        assertFalse(adyenNotificationValidator.isValidIpAddress(FORWARDED_IP));
    }

    @Test
    void shouldLogMismatchErrorWhenIpDoesNotMatchDomain() {
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, NOTIFICATION_DOMAIN))
                .thenReturn(false);

        adyenNotificationValidator.isValidIpAddress(FORWARDED_IP);

        verify(mockAppender, times(1)).doAppend(loggingEventCaptor.capture());
        LoggingEvent loggingEvent = loggingEventCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.INFO));
        assertThat(loggingEvent.getMessage(),
                is("Adyen notification from ip '{}' not matching configured domain '{}'"));
        assertThat(loggingEvent.getArgumentArray()[0], is(FORWARDED_IP));
        assertThat(loggingEvent.getArgumentArray()[1], Is.is(NOTIFICATION_DOMAIN));
    }
}
