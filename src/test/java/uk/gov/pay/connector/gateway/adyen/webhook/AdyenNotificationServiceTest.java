package uk.gov.pay.connector.gateway.adyen.webhook;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.HmacKeys;
import uk.gov.pay.connector.app.adyen.WebhookHmacKeys;
import uk.gov.pay.connector.util.IpDomainMatcher;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_NOTIFICATION;

@ExtendWith(MockitoExtension.class)
class AdyenNotificationServiceTest {

    private AdyenNotificationService adyenNotificationService;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Mock
    private AdyenGatewayConfig adyenGatewayConfig;

    @Mock
    private IpDomainMatcher ipDomainMatcher;

    private String validHmacSigniture = "coqCmt/IZ4E3CzPvMY8zTjQVL5hYJUiBRg8UU+iCWo0="; // pragma: allowlist secret

    @BeforeEach
    void setUp() {
        adyenNotificationService = new AdyenNotificationService(adyenGatewayConfig, ipDomainMatcher);
        Logger root = (Logger) LoggerFactory.getLogger(AdyenNotificationService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    void shouldAcceptNotificationWhenForwardedIpMatchesConfiguredDomain() {
        when(adyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
        when(ipDomainMatcher.ipMatchesDomain("5.6.7.8", "out.adyen.com.")).thenReturn(true);
        when(adyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

        String payload = TestTemplateResourceLoader.load(ADYEN_NOTIFICATION)
                .replace("{{HMAC_SIGNATURE}}", validHmacSigniture);

        boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8");

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

    @Nested
    class TestValidateNotification {
        @BeforeEach
        void setUp() {
            when(adyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
            when(ipDomainMatcher.ipMatchesDomain("5.6.7.8", "out.adyen.com.")).thenReturn(true);
        }

        @Test
        void shouldReturnTrueForValidHmacKey() {
            when(adyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

            String payload = TestTemplateResourceLoader
                    .load(ADYEN_NOTIFICATION)
                    .replace("{{HMAC_SIGNATURE}}", validHmacSigniture);

            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8");

            assertTrue(result);
        }

        @Test
        void shouldReturnFalseWhenHmacSignatureIsInvalid() {
            when(adyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

            String payload = TestTemplateResourceLoader
                    .load(ADYEN_NOTIFICATION)
                    .replace("{{HMAC_SIGNATURE}}", "WrongSignature");

            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8");

            assertFalse(result);
            verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());

            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents
                    .stream()
                    .anyMatch(event -> event
                            .getFormattedMessage()
                            .equals("Invalid HMAC signature in the payload for Adyen notification")), is(true));
        }

        @Test
        void shouldReturnFalseWhenHmacKeyIsInvalid() {
            when(adyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys("invalid-hmac-key"));

            String payload = TestTemplateResourceLoader
                    .load(ADYEN_NOTIFICATION)
                    .replace("{{HMAC_SIGNATURE}}", validHmacSigniture);

            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8");

            assertFalse(result);

            verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents
                    .getFirst()
                    .getFormattedMessage(), is("Failed to validate HMAC signature"));
            assertThat(loggingEvents
                    .get(1)
                    .getFormattedMessage(), is("Failed to validate Adyen notification payload"));
        }

        @Test
        void shouldThrowWebApplicationExceptionWhenPayloadIsInvalidJson() {

            assertThrows(WebApplicationException.class,
                    () -> adyenNotificationService.handleNotificationFor("not-json", "5.6.7.8"));

            verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents
                    .stream()
                    .anyMatch(event -> event
                            .getFormattedMessage()
                            .equals("Error deserialising Adyen notification payload")), is(true));
        }

        @Test
        void shouldReturnFalseWhenPayloadIsValidJsonAndNotificationIsNull() {
            String validJsonButMissingExpectedFields = """ 
                    {
                        "live": false
                    }
                    """;
            boolean result = adyenNotificationService.handleNotificationFor(validJsonButMissingExpectedFields,
                    "5.6.7.8");

            assertFalse(result);

            verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents
                    .getFirst()
                    .getFormattedMessage(), is("Adyen notification request is empty or missing items"));
            assertThat(loggingEvents
                    .get(1)
                    .getFormattedMessage(), is("Failed to validate Adyen notification payload"));
        }

        @Test
        void shouldReturnFalseWhenPayloadIsValidJsonAndNotificationRequestItemsIsEmpty() {
            String validJsonButMissingExpectedFields = """ 
                    {
                        "live": false,
                        "notificationItems": [
                        ]
                    }
                    """;
            boolean result = adyenNotificationService.handleNotificationFor(validJsonButMissingExpectedFields,
                    "5.6.7.8");

            assertFalse(result);
            verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents
                    .getFirst()
                    .getFormattedMessage(), is("Adyen notification request is empty or missing items"));
            assertThat(loggingEvents
                    .get(1)
                    .getFormattedMessage(), is("Failed to validate Adyen notification payload"));
        }
    }

    private HmacKeys getHmacKeys(String... testKey) {
        String exampleLiveKey = "exampleLiveKey";
        String validTestKey = "44782DEF547AAA06C910C43932B1EB0C71FC68D9D0C057550C48EC2ACF6BA056"; // pragma: allowlist secret
        WebhookHmacKeys liveKeys = new WebhookHmacKeys(exampleLiveKey, null);
        WebhookHmacKeys testKeys = testKey == null || testKey.length == 0 ? new WebhookHmacKeys(validTestKey,
                null) : new WebhookHmacKeys(testKey[0], null);

        HmacKeys.WebhookHmacKeyPair pair = new HmacKeys.WebhookHmacKeyPair(testKeys, liveKeys);

        return new HmacKeys(pair);
    }
}
