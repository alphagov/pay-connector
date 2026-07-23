package uk.gov.pay.connector.gateway.adyen.webhook;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
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

    @BeforeEach
    void setUp() {
        adyenNotificationValidator = new AdyenNotificationValidator(ipDomainMatcher);

        Logger logger = (Logger) LoggerFactory.getLogger("uk.gov.pay.connector.gateway.adyen.webhook");
        logger.setLevel(Level.INFO);
        logger.addAppender(mockAppender);
    }

    private static final String NOTIFICATION_DOMAIN = "notification.adyen.com";
    private static final String FORWARDED_IP = "192.168.1.1";
    private static final String FORWARDED_IPS = "192.168.1.1, 10.0.0.1, 172.16.0.1";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldReturnFalseWhenForwardedIpAddressesIsBlankOrNull(String forwardedIpAddresses) {
        assertFalse(adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses, NOTIFICATION_DOMAIN));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldLogMissingXForwardedForHeaderWhenForwardedIpAddressesIsBlankOrNull(String forwardedIpAddresses) {
        adyenNotificationValidator.isValidIpAddress(forwardedIpAddresses, NOTIFICATION_DOMAIN);

        verify(mockAppender, times(1)).doAppend(loggingEventCaptor.capture());
        LoggingEvent loggingEvent = loggingEventCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.INFO));
        assertThat(loggingEvent.getMessage(),
                is("Adyen notification missing X-Forwarded-For header"));
    }

    @Test
    void shouldReturnTrueWhenIpMatchesDomain() {
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, NOTIFICATION_DOMAIN))
                .thenReturn(true);

        assertTrue(adyenNotificationValidator.isValidIpAddress(FORWARDED_IP, NOTIFICATION_DOMAIN));
    }

    @Test
    void shouldReturnFalseWhenIpDoesNotMatchDomain() {
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, NOTIFICATION_DOMAIN))
                .thenReturn(false);

        assertFalse(adyenNotificationValidator.isValidIpAddress(FORWARDED_IP, NOTIFICATION_DOMAIN));
    }

    @Test
    void shouldLogMismatchErrorWhenIpDoesNotMatchDomain() {
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, NOTIFICATION_DOMAIN))
                .thenReturn(false);

        adyenNotificationValidator.isValidIpAddress(FORWARDED_IP, NOTIFICATION_DOMAIN);

        verify(mockAppender, times(1)).doAppend(loggingEventCaptor.capture());
        LoggingEvent loggingEvent = loggingEventCaptor.getValue();
        assertThat(loggingEvent.getLevel(), is(Level.INFO));
        assertThat(loggingEvent.getMessage(),
                is("Adyen notification from ip '{}' not matching configured domain '{}'"));
        assertThat(loggingEvent.getArgumentArray()[0], is(FORWARDED_IP));
        assertThat(loggingEvent.getArgumentArray()[1], is(NOTIFICATION_DOMAIN));
    }

    @Test
    void shouldValidateSuccessfullyWhenMultipleIpAddressesProvided() {
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IPS, NOTIFICATION_DOMAIN))
                .thenReturn(true);

        assertTrue(adyenNotificationValidator.isValidIpAddress(FORWARDED_IPS, NOTIFICATION_DOMAIN));
    }

    @Test
    void shouldCallIpDomainMatcherWithAllForwardedIpsWhenMultipleIpAddressesProvided() {
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IPS, NOTIFICATION_DOMAIN))
                .thenReturn(true);

        adyenNotificationValidator.isValidIpAddress(FORWARDED_IPS, NOTIFICATION_DOMAIN);

        verify(ipDomainMatcher).ipMatchesDomain(FORWARDED_IPS, NOTIFICATION_DOMAIN);
    }
}
