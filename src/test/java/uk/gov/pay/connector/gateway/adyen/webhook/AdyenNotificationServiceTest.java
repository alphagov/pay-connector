package uk.gov.pay.connector.gateway.adyen.webhook;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.notification.WebhookHandler;
import com.adyen.util.HMACValidator;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.HmacKeys;
import uk.gov.pay.connector.app.adyen.WebhookHmacKeys;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.queue.tasks.TaskType;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.pay.connector.util.IpDomainMatcher;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.io.IOException;
import java.security.SignatureException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    private AdyenGatewayConfig mockAdyenGatewayConfig;

    @Mock
    private TaskQueueService mockTaskQueueService;

    @Mock
    private IpDomainMatcher ipDomainMatcher;

    @BeforeEach
    void setUp() {
        adyenNotificationService = new AdyenNotificationService(mockAdyenGatewayConfig, ipDomainMatcher, mockTaskQueueService);
        Logger root = (Logger) LoggerFactory.getLogger(AdyenNotificationService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    void shouldAcceptNotificationWhenForwardedIpMatchesConfiguredDomain() {
        when(mockAdyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
        when(ipDomainMatcher.ipMatchesDomain("5.6.7.8", "out.adyen.com.")).thenReturn(true);
        when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

        String payload = getNotificationWithValidHmacSignature("AUTHORISATION");

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
        when(mockAdyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
        when(ipDomainMatcher.ipMatchesDomain("8.8.8.8", "out.adyen.com.")).thenReturn(false);

        boolean result = adyenNotificationService.handleNotificationFor("{\"notificationItems\":[]}", "8.8.8.8");

        assertFalse(result);
    }

    @Nested
    class TestValidateNotification {
        @BeforeEach
        void setUp() {
            when(mockAdyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
            when(ipDomainMatcher.ipMatchesDomain("5.6.7.8", "out.adyen.com.")).thenReturn(true);
        }

        @Test
        void shouldReturnTrueForValidHmacKey() {
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

            String payload = getNotificationWithValidHmacSignature("AUTHORISATION");

            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8");

            assertTrue(result);
        }

        @Test
        void shouldReturnFalseWhenHmacSignatureIsInvalid() {
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

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
            String validHmacSigniture = "coqCmt/IZ4E3CzPvMY8zTjQVL5hYJUiBRg8UU+iCWo0="; // pragma: allowlist secret

            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys("invalid-hmac-key"));

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

        @ParameterizedTest
        @EnumSource(AdyenPaymentEvent.class)
        void shouldAddTaskToQueueWhenValidCaptureNotificationIsReceived(AdyenPaymentEvent eventCode) {
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

            String payload = getNotificationWithValidHmacSignature(eventCode.toString());

            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8");

            assertTrue(result);

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(mockTaskQueueService).add(taskCaptor.capture());

            Task task = taskCaptor.getValue();
            assertThat(task.getTaskType(), is(TaskType.HANDLE_ADYEN_PAYMENTS_WEBHOOK_NOTIFICATION));
            assertThat(task.getData(), is(payload));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"SOME_INVALID_VALUE"})
        void shouldIgnoreNonCaptureWebhookNotificationsAndNotAddToTaskQue(String eventCode) {
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());
            String payload = getNotificationWithValidHmacSignature(eventCode);

            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8");

            assertTrue(result);

            verify(mockTaskQueueService, never()).add(any(Task.class));
            verify(mockAppender, times(2)).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents.getFirst()
                    .getFormattedMessage(), is("Ignored Adyen notification"));
            assertThat(loggingEvents.get(1).getFormattedMessage(), is("Processed Adyen notification"));
        }

        @Test
        void ShouldNotAddToTaskQueWhenHmacSignatureIsInvalid() {
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

            String payload = TestTemplateResourceLoader.load(ADYEN_NOTIFICATION)
                    .replace("{{HMAC_SIGNATURE}}", "WrongSignature");

            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8");

            assertFalse(result);
            verify(mockTaskQueueService, never()).add(any(Task.class));
        }

        @Test
        void shouldThrowWebApplicationExceptionWhenSendingCaptureNotificationToTaskQueueFails() {
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

            String payload = getNotificationWithValidHmacSignature("CAPTURE");

            doThrow(new RuntimeException("SQS unavailable")).when(mockTaskQueueService).add(any(Task.class));

            WebApplicationException exception = assertThrows(WebApplicationException.class, () ->
                    adyenNotificationService.handleNotificationFor(payload, "5.6.7.8"));

            verify(mockTaskQueueService).add(any(Task.class));
            assertThat(exception.getResponse().getStatus(), is(500));
            verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents.getFirst()
                    .getFormattedMessage(), is("Error sending Adyen webhook notification to task SQS queue"));
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

    private String getNotificationWithValidHmacSignature(String eventCode) {
        try {
            String template = TestTemplateResourceLoader.load(ADYEN_NOTIFICATION)
                    .replace("\"AUTHORISATION\"", "\"" + eventCode + "\"");

            String unsignedPayload = template.replace("{{HMAC_SIGNATURE}}", "");

            NotificationRequest request = new WebhookHandler().handleNotificationJson(unsignedPayload);
            NotificationRequestItem item = request.getNotificationItems().getFirst();

            String hmacKey = "44782DEF547AAA06C910C43932B1EB0C71FC68D9D0C057550C48EC2ACF6BA056"; // pragma: allowlist secret

            String signature = new HMACValidator().calculateHMAC(item, hmacKey); // pragma: allowlist secret

            return template.replace("{{HMAC_SIGNATURE}}", signature);

        } catch (IOException | SignatureException e) {
            throw new RuntimeException(
                    "Failed to build Adyen test notification", e);
        }
    }
}
