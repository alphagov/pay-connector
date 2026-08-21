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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.WebApplicationException;
import org.junit.Assert;
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
import uk.gov.pay.connector.app.adyen.HmacKeys.WebhookHmacKeyPair;
import uk.gov.pay.connector.app.adyen.WebhookHmacKeys;
import uk.gov.pay.connector.gateway.adyen.response.AdyenTokenNotification;
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.queue.tasks.TaskType;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.io.IOException;
import java.security.SignatureException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TOKEN_NOTIFICATION;


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
    private AdyenNotificationValidator mockAdyenNotificationValidator;

    private final JsonObjectMapper jsonObjectMapper = new JsonObjectMapper(new ObjectMapper());
    private static final String FORWARDED_IP = "5.6.7.8";
    private static final String HMAC_SIGNATURE = "sha256=test-signature";

    @BeforeEach
    void setUp() {
        adyenNotificationService = new AdyenNotificationService(mockAdyenGatewayConfig,
                mockTaskQueueService,
                mockAdyenNotificationValidator,
                jsonObjectMapper);
        Logger root = (Logger) LoggerFactory.getLogger(AdyenNotificationService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    void shouldAcceptNotificationWhenForwardedIpMatchesConfiguredDomain() {
        when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
        when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());
        when(mockAdyenNotificationValidator.isValidHmac(any(), any())).thenReturn(true);

        String payload = getNotificationWithValidHmacSignature("AUTHORISATION");

        boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", null);

        assertTrue(result);
    }

    @Test
    void shouldRejectNotificationWhenForwardedIpDoesNotMatchConfiguredDomain() {
        when(mockAdyenNotificationValidator.isValidIpAddress("8.8.8.8")).thenReturn(false);

        boolean result = adyenNotificationService.handleNotificationFor("{\"notificationItems\":[]}", "8.8.8.8", null);

        assertFalse(result);
    }

    @Test
    void shouldRejectNotificationWhenExceptionIsThrown() {
        var primaryTestKey = "primaryTest";
        String payload = TestTemplateResourceLoader.load(ADYEN_TOKEN_NOTIFICATION);
        var tokenKeys = new HmacKeys.WebhookHmacKeyPair(new WebhookHmacKeys(primaryTestKey, "secondaryTest"),
                new WebhookHmacKeys("primaryLive", "secondaryLive"));
        when(mockAdyenNotificationValidator.isValidIpAddress(FORWARDED_IP)).thenReturn(true);
        when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(new HmacKeys(null, tokenKeys));
        when(mockAdyenNotificationValidator.isValidHmac(any(), any(), any())).thenThrow(
                AdyenNotificationException.class);

        boolean result = adyenNotificationService.handleNotificationFor(payload, FORWARDED_IP, HMAC_SIGNATURE);

        assertFalse(result);
        verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
        List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
        assertThat(loggingEvents
                        .stream()
                        .anyMatch(event -> event
                                .getFormattedMessage()
                                .equals("Failed to validate Adyen notification payload")),
                is(true));
    }

    @Nested
    class PaymentNotifications {
        @Test
        void shouldRejectPaymentNotificationWhenHmacSignatureIsInvalid() {
            when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());
            when(mockAdyenNotificationValidator.isValidHmac(any(), any())).thenReturn(false);

            String payload = getNotificationWithValidHmacSignature("AUTHORISATION");

            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", null);

            assertFalse(result);
        }

        @Test
        void shouldNotAddPaymentNotificationToTaskQueueWhenHmacSignatureIsInvalid() {
            String payload = TestTemplateResourceLoader.load(ADYEN_NOTIFICATION)
                    .replace("{{HMAC_SIGNATURE}}", "WrongSignature");

            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", null);

            assertFalse(result);
            verify(mockTaskQueueService, never()).add(any(Task.class));
        }

        @Test
        void shouldThrowWebApplicationExceptionWhenPaymentNotificationPayloadIsInvalidJson() {
            when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
            assertThrows(WebApplicationException.class,
                    () -> adyenNotificationService.handleNotificationFor("not-json", "5.6.7.8", null));

            verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents
                    .stream()
                    .anyMatch(event -> event
                            .getFormattedMessage()
                            .equals("Error deserialising Adyen notification payload")), is(true));
        }

        @Test
        void shouldReturnFalseWhenPayloadIsValidJsonAndPaymentNotificationIsNull() {
            when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
            String validJsonButMissingExpectedFields = """ 
                    {
                        "live": false
                    }
                    """;
            boolean result = adyenNotificationService.handleNotificationFor(validJsonButMissingExpectedFields, "5.6.7.8", null
            );

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
        @NullAndEmptySource
        @ValueSource(strings = {""" 
                {
                    "live": false,
                    "notificationItems": [
                    ]
                }
                """})
        void shouldReturnFalseWhenPayloadIsValidJsonAndNotificationRequestItemsIsEmptyOrNull(String payload) {
            when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", null
            );

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
        @NullAndEmptySource
        @ValueSource(strings = {"SOME_INVALID_VALUE"})
        void shouldNotAddInvalidPaymentNotificationTypeToTaskQueue(String eventCode) {
            when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());
            when(mockAdyenNotificationValidator.isValidHmac(any(), any())).thenReturn(true);
            String payload = getNotificationWithValidHmacSignature(eventCode);

            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", null);

            assertFalse(result);

            verifyNoInteractions(mockTaskQueueService);
        }

        @ParameterizedTest
        @EnumSource(AdyenPaymentEvent.class)
        void shouldThrowWebApplicationExceptionWhenSendingPaymentNotificationToTaskQueueFails(AdyenPaymentEvent eventCode) {
            when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());
            when(mockAdyenNotificationValidator.isValidHmac(any(), any())).thenReturn(true);

            String payload = getNotificationWithValidHmacSignature(eventCode.toString());

            doThrow(new RuntimeException("SQS unavailable"))
                    .when(mockTaskQueueService)
                    .add(any(Task.class));

            WebApplicationException exception = assertThrows(WebApplicationException.class, () ->
                    adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", null));

            verify(mockTaskQueueService).add(any(Task.class));
            assertThat(exception
                    .getResponse()
                    .getStatus(), is(500));
            verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents
                    .getFirst()
                    .getFormattedMessage(), is("Error sending Adyen webhook notification to task SQS queue"));
        }

        @ParameterizedTest
        @EnumSource(AdyenPaymentEvent.class)
        void shouldAddValidPaymentNotificationToTaskQueue(AdyenPaymentEvent eventCode) {
            when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());
            when(mockAdyenNotificationValidator.isValidHmac(any(), any())).thenReturn(true);


            String payload = getNotificationWithValidHmacSignature(eventCode.toString());

            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", null);

            assertTrue(result);

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(mockTaskQueueService).add(taskCaptor.capture());

            Task task = taskCaptor.getValue();
            assertThat(task.getTaskType(), is(TaskType.HANDLE_ADYEN_PAYMENTS_WEBHOOK_NOTIFICATION));
            assertThat(task.getData(), is(payload));
        }
    }

    @Nested
    class TokenNotifications {
        @Test
        void shouldRejectTokenNotificationWhenHmacSignatureIsBlank() {
            String payload = TestTemplateResourceLoader.load(ADYEN_TOKEN_NOTIFICATION);
            when(mockAdyenNotificationValidator.isValidIpAddress(FORWARDED_IP)).thenReturn(true);

            boolean result = adyenNotificationService.handleNotificationFor(payload, FORWARDED_IP, "");

            assertFalse(result);
            verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents
                    .stream()
                    .anyMatch(event -> event
                            .getFormattedMessage()
                            .equals("Hmac signature is invalid or missing, rejecting Adyen token notification")), is(true));
        }

        @Test
        void shouldRejectTokenNotificationWhenHmacSignatureIsInvalid() {
            var primaryTestKey = "primaryTest";
            String payload = TestTemplateResourceLoader.load(ADYEN_TOKEN_NOTIFICATION);
            var tokenKeys = new HmacKeys.WebhookHmacKeyPair(new WebhookHmacKeys(primaryTestKey, "secondaryTest"),
                    new WebhookHmacKeys("primaryLive", "secondaryLive"));
            when(mockAdyenNotificationValidator.isValidIpAddress(FORWARDED_IP)).thenReturn(true);
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(new HmacKeys(null, tokenKeys));
            when(mockAdyenNotificationValidator.isValidHmac(HMAC_SIGNATURE, primaryTestKey,
                    payload)).thenReturn(false);

            boolean result = adyenNotificationService.handleNotificationFor(payload, FORWARDED_IP, HMAC_SIGNATURE);

            assertFalse(result);
            verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents
                            .stream()
                            .anyMatch(event -> event
                                    .getFormattedMessage()
                                    .equals("Hmac signature is invalid or missing, rejecting Adyen token notification")),
                    is(true));
            verifyNoInteractions(mockTaskQueueService);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"recurring.token.deleted"})
        void shouldNotAddInvalidTokenNotificationTypeToTaskQueue(String eventCode) {
            String payload = TestTemplateResourceLoader.load(ADYEN_TOKEN_NOTIFICATION).replace("\"recurring.token.created\"", "\"" + eventCode + "\"");
            when(mockAdyenNotificationValidator.isValidIpAddress(FORWARDED_IP)).thenReturn(true);

            boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", HMAC_SIGNATURE);

            assertFalse(result);
            verifyNoInteractions(mockTaskQueueService);
        }

        @ParameterizedTest
        @EnumSource(AdyenTokenEvent.class)
        void shouldAcceptValidTokenNotification(AdyenTokenEvent eventCode) {
            var primaryTestKey = "primaryTest";
            String payload = TestTemplateResourceLoader.load(ADYEN_TOKEN_NOTIFICATION).replace("recurring.token.created", eventCode.getName());
            var tokenKeys = new HmacKeys.WebhookHmacKeyPair(new WebhookHmacKeys(primaryTestKey, "secondaryTest"),
                    new WebhookHmacKeys("primaryLive", "secondaryLive"));
            when(mockAdyenNotificationValidator.isValidIpAddress(FORWARDED_IP)).thenReturn(true);
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(new HmacKeys(null, tokenKeys));
            when(mockAdyenNotificationValidator.isValidHmac(HMAC_SIGNATURE, primaryTestKey,
                    payload)).thenReturn(true);

            boolean result = adyenNotificationService.handleNotificationFor(payload, FORWARDED_IP, HMAC_SIGNATURE);

            assertTrue(result);

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);

            verify(mockTaskQueueService).add(taskCaptor.capture());

            Task task = taskCaptor.getValue();

            assertThat(task.getTaskType(), is(TaskType.HANDLE_ADYEN_TOKEN_WEBHOOK_NOTIFICATION));
            assertThat(task.getData(), is(payload));
        }

        @Test
        void shouldThrowExceptionWhenTokenNotificationPayloadIsInvalid() {
            WebApplicationException exception = Assert.assertThrows(
                    WebApplicationException.class,
                    () -> adyenNotificationService.deserialiseTokenPayload("invalidJson", AdyenTokenNotification.class)
            );

            verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();
            assertThat(loggingEvents
                            .stream()
                            .anyMatch(event -> event
                                    .getFormattedMessage()
                                    .equals("Error deserialising token notification payload")),
                    is(true));

            assertThat("Error deserialising token webhook Json", is(exception.getMessage()));
        }

        @ParameterizedTest
        @EnumSource(AdyenTokenEvent.class)
        void shouldThrowWebApplicationExceptionWhenAddingTokenNotificationToTaskQueueFails(AdyenTokenEvent eventCode) {
            var primaryTestKey = "primaryTest";

            String payload = TestTemplateResourceLoader.load(
                    ADYEN_TOKEN_NOTIFICATION
            ).replace("recurring.token.created", eventCode.getName());

            var tokenKeys = new HmacKeys.WebhookHmacKeyPair(
                    new WebhookHmacKeys(primaryTestKey, "secondaryTest"),
                    new WebhookHmacKeys("primaryLive", "secondaryLive")
            );

            when(mockAdyenNotificationValidator.isValidIpAddress(FORWARDED_IP))
                    .thenReturn(true);

            when(mockAdyenGatewayConfig.getHmacKeys())
                    .thenReturn(new HmacKeys(null, tokenKeys));

            when(mockAdyenNotificationValidator.isValidHmac(HMAC_SIGNATURE, primaryTestKey, payload)).thenReturn(true);

            doThrow(new RuntimeException("SQS unavailable"))
                    .when(mockTaskQueueService)
                    .add(any(Task.class));

            Assert.assertThrows(WebApplicationException.class, () -> adyenNotificationService.handleNotificationFor(
                    payload, FORWARDED_IP, HMAC_SIGNATURE));
            verify(mockAppender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());

            List<LoggingEvent> loggingEvents = loggingEventArgumentCaptor.getAllValues();

            assertThat(loggingEvents
                    .stream()
                    .anyMatch(event -> event
                            .getFormattedMessage()
                            .equals("Error sending Adyen webhook notification to task SQS queue")), is(true)
            );
        }
    }

    private HmacKeys getHmacKeys(String... testKey) {
        String exampleLiveKey = "exampleLiveKey";
        String validTestKey = "44782DEF547AAA06C910C43932B1EB0C71FC68D9D0C057550C48EC2ACF6BA056"; // pragma: allowlist secret
        WebhookHmacKeys liveKeys = new WebhookHmacKeys(exampleLiveKey, null);
        WebhookHmacKeys testKeys = testKey == null || testKey.length == 0 ? new WebhookHmacKeys(validTestKey,
                null) : new WebhookHmacKeys(testKey[0], null);

        WebhookHmacKeyPair pair = new WebhookHmacKeyPair(testKeys, liveKeys);

        return new HmacKeys(pair, null);
    }

    private String getNotificationWithValidHmacSignature(String eventCode) {
        try {
            String template = TestTemplateResourceLoader
                    .load(ADYEN_NOTIFICATION)
                    .replace("\"AUTHORISATION\"", "\"" + eventCode + "\"");

            String unsignedPayload = template.replace("{{HMAC_SIGNATURE}}", "");

            NotificationRequest request = new WebhookHandler().handleNotificationJson(unsignedPayload);
            NotificationRequestItem item = request
                    .getNotificationItems()
                    .getFirst();

            String hmacKey = "44782DEF547AAA06C910C43932B1EB0C71FC68D9D0C057550C48EC2ACF6BA056"; // pragma: allowlist secret

            String signature = new HMACValidator().calculateHMAC(item, hmacKey); // pragma: allowlist secret

            return template.replace("{{HMAC_SIGNATURE}}", signature);

        } catch (IOException | SignatureException e) {
            throw new RuntimeException(
                    "Failed to build Adyen test notification", e);
        }
    }
}
