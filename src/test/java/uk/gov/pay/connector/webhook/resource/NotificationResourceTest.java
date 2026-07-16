package uk.gov.pay.connector.webhook.resource;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenRecurringTokenNotificationService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationResourceTest {

    @Mock
    AdyenNotificationService adyenNotificationService;

    @Mock
    AdyenRecurringTokenNotificationService adyenRecurringTokenNotificationService;

    @InjectMocks
    private NotificationResource notificationResource;

    @Test
    void shouldReturn200WhenAdyenNotificationSuccessfullyHandled() {
        String rawNotification = "{Adyen}";
        String forwardedIpAddress = "10.20.30.40";
        when(adyenNotificationService.handleNotificationFor(anyString(), eq(forwardedIpAddress))).thenReturn(true);

        try (Response response = notificationResource.authoriseAdyenPaymentsNotifications(rawNotification, forwardedIpAddress)) {
            assertThat(response.getStatus(), is(200));
        }
    }

    @Test
    void shouldReturn403WhenAdyenNotificationValidationFails() {
        String rawNotification = "{Adyen}";
        String forwardedIpAddress = " ";
        when(adyenNotificationService.handleNotificationFor(anyString(), eq(forwardedIpAddress))).thenReturn(false);

        try (Response response = notificationResource.authoriseAdyenPaymentsNotifications(rawNotification, forwardedIpAddress)) {
            assertThat(response.getStatus(), is(403));
        }
    }

    @Test
    void shouldReturn200WhenAdyenRecurringTokenNotificationSuccessfullyHandled() {
        String rawNotification = "{\"type\":\"recurring.token.created\"}";
        String forwardedIpAddress = "10.20.30.40";
        String hmacSignature = "sha256=test-signature";
        when(adyenRecurringTokenNotificationService.handleNotificationFor(rawNotification, hmacSignature, forwardedIpAddress)).thenReturn(true);

        try (Response response = notificationResource.authoriseAdyenRecurringTokenNotifications(rawNotification, forwardedIpAddress, hmacSignature)) {
            assertThat(response.getStatus(), is(200));
        }

        verify(adyenRecurringTokenNotificationService).handleNotificationFor(rawNotification, hmacSignature, forwardedIpAddress);
    }

    @Test
    void shouldReturn403WhenAdyenTokenNotificationValidationFails() {
        String rawNotification = "{\"type\":\"recurring.token.created\"}";
        String forwardedIpAddress = " ";
        String hmacSignature = "sha256=test-signature";
        when(adyenRecurringTokenNotificationService.handleNotificationFor(rawNotification, hmacSignature, forwardedIpAddress)).thenReturn(false);

        try (Response response = notificationResource.authoriseAdyenRecurringTokenNotifications(rawNotification, forwardedIpAddress, hmacSignature)) {
            assertThat(response.getStatus(), is(403));
        }
    }
}
