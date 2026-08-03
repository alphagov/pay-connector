package uk.gov.pay.connector.gateway.adyen.webhook;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;
import uk.gov.pay.connector.util.IpDomainMatcher;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.security.SignatureException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_NOTIFICATION;

@ExtendWith(MockitoExtension.class)
class AdyenNotificationValidatorTest {
    public static final String INVALID_HMAC_SIGNATURE = "invalidHmacSignature";
    private AdyenNotificationValidator adyenNotificationValidator;

    @Mock
    private IpDomainMatcher ipDomainMatcher;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventCaptor;

    @Mock
    private AdyenGatewayConfig gatewayConfig;

    @Mock
    HMACValidator hmacValidator;

    public static final String NOTIFICATION_DOMAIN = "notification.adyen.com";

    @BeforeEach
    void setUp() {
        when(gatewayConfig.getNotificationDomain()).thenReturn(NOTIFICATION_DOMAIN);
        adyenNotificationValidator = new AdyenNotificationValidator(gatewayConfig, ipDomainMatcher, hmacValidator);

        Logger logger = (Logger) LoggerFactory.getLogger(AdyenNotificationValidator.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(mockAppender);
    }

    private static final String FORWARDED_IP = "192.168.1.1";

    @Nested
    class TestIpValidation {
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
            when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, NOTIFICATION_DOMAIN)).thenReturn(true);

            assertTrue(adyenNotificationValidator.isValidIpAddress(FORWARDED_IP));
            verify(ipDomainMatcher).ipMatchesDomain(FORWARDED_IP, NOTIFICATION_DOMAIN);
        }

        @Test
        void shouldReturnFalseAndLogMismatchErrorWhenIpDoesNotMatchDomain() {
            when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, NOTIFICATION_DOMAIN))
                    .thenReturn(false);

            boolean result = adyenNotificationValidator.isValidIpAddress(FORWARDED_IP);

            assertFalse(result);
            verify(mockAppender, times(1)).doAppend(loggingEventCaptor.capture());
            LoggingEvent loggingEvent = loggingEventCaptor.getValue();
            assertThat(loggingEvent.getLevel(), is(Level.INFO));
            assertThat(loggingEvent.getMessage(),
                    is("Adyen notification from ip '{}' not matching configured domain '{}'"));
            assertThat(loggingEvent.getArgumentArray()[0], is(FORWARDED_IP));
            assertThat(loggingEvent.getArgumentArray()[1], Is.is(NOTIFICATION_DOMAIN));
        }
    }

    @Nested
    class TestHmacValidation {
        private final String validHmacSignature = "44782DEF547AAA06C910C43932B1EB0C71FC68D9D0C057550C48EC2ACF6BA056"; // pragma: allowlist secret
        private final JsonObjectMapper mapper = new JsonObjectMapper(new ObjectMapper());

        @Test
        void shouldReturnTrueForValidHmacSignature() throws SignatureException {
            when(hmacValidator.validateHMAC(any(), any())).thenReturn(true);

            var item = loadNotificationItem(validHmacSignature);


            var result = adyenNotificationValidator.isValidHmac(item, validHmacSignature);
            assertTrue(result);
        }

        @Test
        void shouldReturnFalseAndLogWhenHmacSignatureIsInvalid() throws SignatureException {
            when(hmacValidator.validateHMAC(any(), any())).thenReturn(false);

            var item = loadNotificationItem(INVALID_HMAC_SIGNATURE);

            var result = adyenNotificationValidator.isValidHmac(item, INVALID_HMAC_SIGNATURE);

            assertFalse(result);
            verify(mockAppender, times(1)).doAppend(loggingEventCaptor.capture());

            List<LoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();
            assertThat(loggingEvents
                    .stream()
                    .anyMatch(event -> event
                            .getFormattedMessage()
                            .equals("Invalid HMAC signature in the payload for Adyen notification")), is(true));
        }

        @Test
        void shouldThrowExceptionWhenAdyenPaymentNotificationItemIsNotValid() throws SignatureException {
            when(hmacValidator.validateHMAC(any(), any())).thenThrow(IllegalArgumentException.class);

            assertThrows(AdyenNotificationException.class, () ->
                    adyenNotificationValidator.isValidHmac(new NotificationRequestItem(), validHmacSignature)
            );

            verify(mockAppender, times(1)).doAppend(loggingEventCaptor.capture());

            List<LoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();
            assertThat(loggingEvents
                    .stream()
                    .anyMatch(event -> event
                            .getFormattedMessage()
                            .equals("Failed to validate HMAC signature")), is(true));
        }

        private NotificationRequestItem loadNotificationItem(String hmacSignature) {
            return mapper.getObject(TestTemplateResourceLoader
                                    .load(ADYEN_NOTIFICATION)
                                    .replace("{{HMAC_SIGNATURE}}", hmacSignature),
                            NotificationRequest.class)
                    .getNotificationItems()
                    .getFirst();
        }

    }

    @Nested
    class TestHmacValidationWithSignatureForTokens {
        private final String validHmacSignature = "44782DEF547AAA06C910C43932B1EB0C71FC68D9D0C057550C48EC2ACF6BA056"; // pragma: allowlist secret
        private final String hmacKey = "ValidHmacKey";
        private final String payload = "Validpayload";

        @Test
        void shouldReturnTrueForValidTokenSignature() throws SignatureException {
            when(hmacValidator.validateHMAC(validHmacSignature, hmacKey, payload)).thenReturn(true);

            var result = adyenNotificationValidator.isValidHmac(validHmacSignature, hmacKey, payload);
            assertTrue(result);
        }

        @Test
        void shouldReturnFalseForInvalidTokenSignature() throws SignatureException {
            when(hmacValidator.validateHMAC(validHmacSignature, hmacKey, payload)).thenReturn(false);

            var result = adyenNotificationValidator.isValidHmac(validHmacSignature, hmacKey, payload);
            assertFalse(result);
        }

        @Test
        void shouldThrowExceptionWhenAdyenPaymentTokenNotificationIsNotValid() throws SignatureException {
            when(hmacValidator.validateHMAC(any(), any(), any())).thenThrow(IllegalArgumentException.class);

            assertThrows(AdyenNotificationException.class, () ->
                    adyenNotificationValidator.isValidHmac("some signature", "some hmac key", 
                            "some payload")
            );

            verify(mockAppender, times(1)).doAppend(loggingEventCaptor.capture());

            List<LoggingEvent> loggingEvents = loggingEventCaptor.getAllValues();
            assertThat(loggingEvents
                    .stream()
                    .anyMatch(event -> event
                            .getFormattedMessage()
                            .equals("Failed to validate HMAC signature for token notification")), is(true));
        }
    }

}
