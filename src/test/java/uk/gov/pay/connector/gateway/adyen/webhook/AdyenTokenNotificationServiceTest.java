package uk.gov.pay.connector.gateway.adyen.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.HmacKeys;
import uk.gov.pay.connector.app.adyen.WebhookHmacKeys;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.queue.tasks.TaskType;
import uk.gov.pay.connector.queue.tasks.model.Task;
import uk.gov.pay.connector.util.IpDomainMatcher;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

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
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TOKEN_CREATED_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TOKEN_DISABLED_NOTIFICATION;

@ExtendWith(MockitoExtension.class)
class AdyenTokenNotificationServiceTest {

    private static final String VALID_HMAC_SIGNATURE = "AY06WfSC+Rp6S6HKwJn0/cK/A79/b6J90ZYV9eLi/LM="; // pragma: allowlist secret
    private static final String FORWARDED_IP = "5.6.7.8";

    @Mock
    private AdyenGatewayConfig mockAdyenGatewayConfig;

    @Mock
    private TaskQueueService mockTaskQueueService;

    @Mock
    private IpDomainMatcher ipDomainMatcher;

    @Captor
    private ArgumentCaptor<Task> taskCaptor;

    private AdyenTokenNotificationService adyenTokenNotificationService;

    @BeforeEach
    void setUp() {
        adyenTokenNotificationService = new AdyenTokenNotificationService(
                mockAdyenGatewayConfig,
                ipDomainMatcher,
                mockTaskQueueService,
                new ObjectMapper());
    }

    @Test
    void shouldAcceptTokenCreatedNotificationWhenIpAndHmacAreValid() {
        when(mockAdyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, "out.adyen.com.")).thenReturn(true);
        when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

        String payload = TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION);

        boolean result = adyenTokenNotificationService.handleNotificationFor(payload, VALID_HMAC_SIGNATURE, FORWARDED_IP);

        assertTrue(result);
        verify(mockTaskQueueService).add(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getTaskType(), is(TaskType.HANDLE_ADYEN_TOKEN_WEBHOOK_NOTIFICATION));
        assertThat(taskCaptor.getValue().getData(), is(payload));
    }

    @Test
    void shouldAcceptTokenDisabledNotificationWhenIpAndHmacAreValid() {
        when(mockAdyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, "out.adyen.com.")).thenReturn(true);
        when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

        String payload = TestTemplateResourceLoader.load(ADYEN_TOKEN_DISABLED_NOTIFICATION);
        String hmacSignature = "/3fe3d6NSYGFlyl3MGiB7Dedz/KXrOPMY6fupoE35qo="; // pragma: allowlist secret

        boolean result = adyenTokenNotificationService.handleNotificationFor(payload, hmacSignature, FORWARDED_IP);

        assertTrue(result);
        verify(mockTaskQueueService).add(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getTaskType(), is(TaskType.HANDLE_ADYEN_TOKEN_WEBHOOK_NOTIFICATION));
    }

    @Test
    void shouldRejectNotificationWhenForwardedIpHeaderIsMissing() {
        boolean result = adyenTokenNotificationService.handleNotificationFor("{}", VALID_HMAC_SIGNATURE, null);

        assertFalse(result);
        verifyNoInteractions(ipDomainMatcher);
    }

    @Test
    void shouldRejectNotificationWhenForwardedIpDoesNotMatchConfiguredDomain() {
        when(mockAdyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
        when(ipDomainMatcher.ipMatchesDomain("8.8.8.8", "out.adyen.com.")).thenReturn(false);

        boolean result = adyenTokenNotificationService.handleNotificationFor("{}", VALID_HMAC_SIGNATURE, "8.8.8.8");

        assertFalse(result);
    }

    @Test
    void shouldRejectNotificationWhenHmacSignatureHeaderIsMissing() {
        when(mockAdyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, "out.adyen.com.")).thenReturn(true);

        String payload = TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION);

        boolean result = adyenTokenNotificationService.handleNotificationFor(payload, null, FORWARDED_IP);

        assertFalse(result);
        verify(mockTaskQueueService, never()).add(any(Task.class));
    }

    @Test
    void shouldRejectNotificationWhenHmacSignatureIsInvalid() {
        when(mockAdyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, "out.adyen.com.")).thenReturn(true);
        when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

        String payload = TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION);

        boolean result = adyenTokenNotificationService.handleNotificationFor(payload, "invalid-signature", FORWARDED_IP);

        assertFalse(result);
        verify(mockTaskQueueService, never()).add(any(Task.class));
    }

    @Test
    void shouldIgnoreUnsupportedNotificationType() {
        when(mockAdyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, "out.adyen.com.")).thenReturn(true);
        when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

        String payload = """
                {"createdAt":"2025-06-30T16:40:23+02:00","eventId":"QBQQ9DLNRHHKGK38","environment":"test","data":{},"type":"recurring.token.updated"}
                """.trim();
        String hmacSignature = "XG8JIu+KdqQxF2/Medl3Az4VcpH/OC8wK2ZTwbk03Io="; // pragma: allowlist secret

        boolean result = adyenTokenNotificationService.handleNotificationFor(payload, hmacSignature, FORWARDED_IP);

        assertTrue(result);
        verify(mockTaskQueueService, never()).add(any(Task.class));
    }

    @Test
    void shouldThrowWebApplicationExceptionWhenSendingTokenNotificationToTaskQueueFails() {
        when(mockAdyenGatewayConfig.getNotificationDomain()).thenReturn("out.adyen.com.");
        when(ipDomainMatcher.ipMatchesDomain(FORWARDED_IP, "out.adyen.com.")).thenReturn(true);
        when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(getHmacKeys());

        String payload = TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION);
        doThrow(new RuntimeException("SQS unavailable")).when(mockTaskQueueService).add(any(Task.class));

        WebApplicationException exception = assertThrows(WebApplicationException.class, () ->
                adyenTokenNotificationService.handleNotificationFor(payload, VALID_HMAC_SIGNATURE, FORWARDED_IP));

        verify(mockTaskQueueService).add(any(Task.class));
        assertThat(exception.getResponse().getStatus(), is(500));
    }

    private HmacKeys getHmacKeys() {
        String validTestPaymentKey = "44782DEF547AAA06C910C43932B1EB0C71FC68D9D0C057550C48EC2ACF6BA056"; // pragma: allowlist secret
        String validTestTokenKey = "6D5BADA576A73109D879220DCB793FFD67DEF7AA18C74CCC0AB66FD87AC8AEEA"; // pragma: allowlist secret
        WebhookHmacKeys liveKeys = new WebhookHmacKeys("exampleLiveKey", null);
        WebhookHmacKeys paymentTestKeys = new WebhookHmacKeys(validTestPaymentKey, null);
        WebhookHmacKeys tokenTestKeys = new WebhookHmacKeys(validTestTokenKey, null);

        return new HmacKeys(
                new HmacKeys.WebhookHmacKeyPair(paymentTestKeys, liveKeys),
                new HmacKeys.WebhookHmacKeyPair(tokenTestKeys, liveKeys));
    }
}
