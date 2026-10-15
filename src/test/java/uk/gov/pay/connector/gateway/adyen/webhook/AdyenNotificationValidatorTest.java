package uk.gov.pay.connector.gateway.adyen.webhook;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.HmacKeys;
import uk.gov.pay.connector.app.adyen.WebhookHmacKeys;
import uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookNotification;
import uk.gov.pay.connector.gateway.exception.AdyenNotificationException;
import uk.gov.pay.connector.util.IpDomainMatcher;
import uk.gov.pay.connector.util.JsonObjectMapper;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.security.SignatureException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenEnvironment.TEST;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookEvent.CAPTURE;
import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenWebhookEvent.RECURRING_TOKEN_CREATED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TOKEN_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class AdyenNotificationValidatorTest {
    public static final String INVALID_HMAC_SIGNATURE = "invalidHmacSignature";
    private AdyenNotificationValidator adyenNotificationValidator;

    @Mock
    private IpDomainMatcher ipDomainMatcher;

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenNotificationValidator.class);

    @Mock
    private AdyenGatewayConfig gatewayConfig;

    @Mock
    HMACValidator hmacValidator;

    @Mock
    AdyenWebhookDeserialiser mockAdyenWebhookDeserialiser;

    public static final String NOTIFICATION_DOMAIN = "notification.adyen.com";

    @BeforeEach
    void setUp() {
        when(gatewayConfig.getNotificationDomain()).thenReturn(NOTIFICATION_DOMAIN);
        adyenNotificationValidator = new AdyenNotificationValidator(gatewayConfig, ipDomainMatcher, hmacValidator, mockAdyenWebhookDeserialiser);
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

            logs.assertContains("Adyen notification missing X-Forwarded-For header");
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

            logs.assertContains("Adyen notification from ip '192.168.1.1' not matching configured domain 'notification.adyen.com'");
        }
    }

    @Nested
    class TestHmacValidation {
        private final String validHmacSignature = "44782DEF547AAA06C910C43932B1EB0C71FC68D9D0C057550C48EC2ACF6BA056"; // pragma: allowlist secret
        private final JsonObjectMapper mapper = new JsonObjectMapper(new ObjectMapper());

        @ParameterizedTest
        @ValueSource(strings = {"payments", "tokens"})
        void shouldDelegateHmacValidationBasedOnUsesAdyenNotificationItemField(String notificationType) throws SignatureException {
            var keyPair = new HmacKeys.WebhookHmacKeyPair(new WebhookHmacKeys("primaryTest", "secondaryTest"),
                    new WebhookHmacKeys("primaryLive", "secondaryLive"));
            var hmacKeys = notificationType.equals("payments") ? new HmacKeys(keyPair, null, null, null, null) : new HmacKeys(null, keyPair, null, null, null);
            var event = notificationType.equals("payments") ? CAPTURE : RECURRING_TOKEN_CREATED;
            var notification = new AdyenWebhookNotification(event, TEST, notificationType.equals("payments"));
            var payload = load(notificationType.equals("payments") ? ADYEN_NOTIFICATION : ADYEN_TOKEN_NOTIFICATION);

            when(gatewayConfig.getHmacKeys()).thenReturn(hmacKeys);
            if (notificationType.equals("payments")) {
                when(hmacValidator.validateHMAC(any(), any())).thenReturn(true);
            } else {
                when(hmacValidator.validateHMAC(any(), any(), any())).thenReturn(true);
            }

            var result = adyenNotificationValidator.validateHmacSignature(notification, payload, notificationType.equals("tokens") ? validHmacSignature : null);

            assertTrue(result);
        }

        @Test
        void shouldThrowExceptionWhenHmacSignatureFromHeaderIsMissing() {
            var keys = new HmacKeys.WebhookHmacKeyPair(new WebhookHmacKeys("primaryTest", "secondaryTest"),
                    new WebhookHmacKeys("primaryLive", "secondaryLive"));

            when(gatewayConfig.getHmacKeys()).thenReturn(new HmacKeys(null, keys, null, null, null));
            var notification = new AdyenWebhookNotification(RECURRING_TOKEN_CREATED, TEST, false);

            assertThrows(AdyenNotificationException.class,
                    () -> adyenNotificationValidator.validateHmacSignature(notification, load(ADYEN_TOKEN_NOTIFICATION), ""));

            logs.assertContains("Hmac signature is missing, rejecting Adyen token notification");
        }

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

            logs.assertContains("Invalid HMAC signature in the payload for Adyen notification");
        }

        @Test
        void shouldThrowExceptionWhenAdyenPaymentNotificationItemIsNotValid() throws SignatureException {
            when(hmacValidator.validateHMAC(any(), any())).thenThrow(IllegalArgumentException.class);

            assertThrows(AdyenNotificationException.class, () ->
                    adyenNotificationValidator.isValidHmac(new NotificationRequestItem(), validHmacSignature)
            );

            logs.assertContains("Failed to validate HMAC signature");
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
        private final String payload = "ValidPayload";

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

            logs.assertContains("Failed to validate HMAC signature for notification");
        }
    }

}
