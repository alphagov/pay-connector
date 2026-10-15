package uk.gov.pay.connector.gateway.adyen.webhook;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.notification.WebhookHandler;
import com.adyen.util.HMACValidator;
import io.github.netmikey.logunit.api.LogCapturer;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookEvent;
import uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookNotification;
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.AdyenBalanceReportNotificationHandler;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.io.IOException;
import java.security.SignatureException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenEnvironment.TEST;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookEvent.AUTHORISATION;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookEvent.BALANCE_PLATFORM_REPORT_CREATED;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookEvent.EXPIRE;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookEvent.RECURRING_TOKEN_CREATED;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookEvent.TRANSFER_CREATED;
import static uk.gov.pay.connector.queue.tasks.TaskType.HANDLE_ADYEN_TOKEN_WEBHOOK_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_BALANCE_PLATFORM_REPORT_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TOKEN_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TRANSFER_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class AdyenNotificationServiceTest {

    private AdyenNotificationService adyenNotificationService;

    @Mock
    private TaskQueueService mockTaskQueueService;

    @Mock
    private AdyenNotificationValidator mockAdyenNotificationValidator;

    @Mock
    private AdyenWebhookNotificationParser mockAdyenWebhookNotificationParser;

    @Mock
    private AdyenBalanceReportNotificationHandler mockAdyenBalanceReportNotificationHandler;

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenNotificationService.class);

    private static final String FORWARDED_IP = "5.6.7.8";
    private static final String HMAC_SIGNATURE = "sha256=test-signature";

    @BeforeEach
    void setUp() {
        adyenNotificationService = new AdyenNotificationService(
                mockTaskQueueService,
                mockAdyenNotificationValidator,
                mockAdyenWebhookNotificationParser,
                mockAdyenBalanceReportNotificationHandler);
    }

    @Test
    void shouldAcceptNotificationWhenForwardedIpMatchesConfiguredDomain() {
        String payload = load(ADYEN_TRANSFER_NOTIFICATION).replace("{{type}}", "balancePlatform.transfer.created");
        when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
        when(mockAdyenWebhookNotificationParser.parse(payload)).thenReturn(getAdyenWebhookNotification(TRANSFER_CREATED, false));
        when(mockAdyenNotificationValidator.validateHmacSignature(any(), any(), any())).thenReturn(true);

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
        String payload = TestTemplateResourceLoader.load(ADYEN_TOKEN_NOTIFICATION);
        when(mockAdyenNotificationValidator.isValidIpAddress(FORWARDED_IP)).thenReturn(true);
        when(mockAdyenWebhookNotificationParser.parse(payload)).thenThrow(new AdyenNotificationException("Error parsing payload"));

        boolean result = adyenNotificationService.handleNotificationFor(payload, FORWARDED_IP, HMAC_SIGNATURE);

        assertFalse(result);

        logs.assertContains("Failed to process Adyen notification - Error parsing payload");
    }

    @Test
    void shouldRejectPaymentNotificationWhenHmacSignatureIsInvalid() {
        String payload = getNotificationWithValidHmacSignature("AUTHORISATION");
        when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
        when(mockAdyenWebhookNotificationParser.parse(payload)).thenReturn(getAdyenWebhookNotification(AUTHORISATION, true));
        when(mockAdyenNotificationValidator.validateHmacSignature(any(), any(), any())).thenReturn(false);

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
    void shouldThrowWebApplicationExceptionWhenSendingPaymentNotificationToTaskQueueFails() {
        String payload = getNotificationWithValidHmacSignature("AUTHORISATION");
        when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
        when(mockAdyenWebhookNotificationParser.parse(payload)).thenReturn(getAdyenWebhookNotification(AUTHORISATION, true));
        when(mockAdyenNotificationValidator.validateHmacSignature(any(), any(), any())).thenReturn(true);

        doThrow(new RuntimeException("SQS unavailable"))
                .when(mockTaskQueueService)
                .add(any(Task.class));

        WebApplicationException exception = assertThrows(WebApplicationException.class, () ->
                adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", null));

        verify(mockTaskQueueService).add(any(Task.class));
        assertThat(exception
                .getResponse()
                .getStatus(), is(500));
        logs.assertContains("Error sending Adyen webhook notification to task SQS queue");
    }

    @Test
    void shouldThrowWebApplicationExceptionWhenNotificationCannotBeParse() {
        String payload = "invalid-json";
        when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
        when(mockAdyenWebhookNotificationParser.parse(payload)).thenThrow(new UnparseableAdyenWebhookException("No recognised field in the payload to process Adyen notification"));

        WebApplicationException exception = assertThrows(WebApplicationException.class, () ->
                adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", null));

        verifyNoInteractions(mockTaskQueueService);
        assertThat(exception
                .getResponse()
                .getStatus(), is(500));
        logs.assertContains("Unabled to parse Adyen notification - No recognised field in the payload to process Adyen notification");
    }

    @Test
    void shouldNotAddIgnoredEventToTaskQueue() {
        String payload = getNotificationWithValidHmacSignature("EXPIRE");
        when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
        when(mockAdyenWebhookNotificationParser.parse(payload)).thenReturn(getAdyenWebhookNotification(EXPIRE, true));

        boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", null);

        assertTrue(result);
        verifyNoInteractions(mockTaskQueueService);
    }

    @Test
    void shouldAddValidNotificationToTaskQueue() {
        String payload = getNotificationWithValidHmacSignature("RECURRING_TOKEN_CREATED");
        when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
        when(mockAdyenWebhookNotificationParser.parse(payload)).thenReturn(getAdyenWebhookNotification(RECURRING_TOKEN_CREATED, false));
        when(mockAdyenNotificationValidator.validateHmacSignature(any(), any(), any())).thenReturn(true);

        boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", null);

        assertTrue(result);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(mockTaskQueueService).add(taskCaptor.capture());

        Task task = taskCaptor.getValue();

        assertThat(task.getTaskType(), is(HANDLE_ADYEN_TOKEN_WEBHOOK_NOTIFICATION));
        assertThat(task.getData(), is(payload));
    }

    @Test
    void shouldAddBalanceReportWebhookToPayoutReconcileQueue() {
        String payload = TestTemplateResourceLoader.load(ADYEN_BALANCE_PLATFORM_REPORT_NOTIFICATION);
        when(mockAdyenNotificationValidator.isValidIpAddress("5.6.7.8")).thenReturn(true);
        when(mockAdyenWebhookNotificationParser.parse(payload)).thenReturn(getAdyenWebhookNotification(BALANCE_PLATFORM_REPORT_CREATED, false));
        when(mockAdyenNotificationValidator.validateHmacSignature(any(), any(), any())).thenReturn(true);

        boolean result = adyenNotificationService.handleNotificationFor(payload, "5.6.7.8", null);

        assertTrue(result);

        verifyNoInteractions(mockTaskQueueService);
        verify(mockAdyenBalanceReportNotificationHandler).process(payload);
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

    private AdyenWebhookNotification getAdyenWebhookNotification(AdyenWebhookEvent adyenWebhookEvent, boolean usesNotificationItems) {
        return new AdyenWebhookNotification(adyenWebhookEvent, TEST, usesNotificationItems);
    }
}
