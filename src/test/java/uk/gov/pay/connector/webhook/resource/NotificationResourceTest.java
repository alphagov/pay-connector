package uk.gov.pay.connector.webhook.resource;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenNotificationService;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenTokenNotificationService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationResourceTest {

    @Mock
    AdyenNotificationService adyenNotificationService;

    @Mock
    AdyenTokenNotificationService adyenTokenNotificationService;

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
    void shouldReturn200WhenAdyenTokenNotificationSuccessfullyHandled() {
        String rawNotification = "{Adyen token}";
        String hmacSignature = "signature";
        String forwardedIpAddress = "10.20.30.40";
        when(adyenTokenNotificationService.handleNotificationFor(anyString(), eq(hmacSignature), eq(forwardedIpAddress)))
                .thenReturn(true);

        try (Response response = notificationResource.authoriseAdyenTokenNotifications(rawNotification, hmacSignature, forwardedIpAddress)) {
            assertThat(response.getStatus(), is(200));
        }
    }

    @Test
    void shouldReturn403WhenAdyenTokenNotificationValidationFails() {
        String rawNotification = "{Adyen token}";
        String hmacSignature = "signature";
        String forwardedIpAddress = " ";
        when(adyenTokenNotificationService.handleNotificationFor(anyString(), eq(hmacSignature), eq(forwardedIpAddress)))
                .thenReturn(false);

        try (Response response = notificationResource.authoriseAdyenTokenNotifications(rawNotification, hmacSignature, forwardedIpAddress)) {
            assertThat(response.getStatus(), is(403));
        }
    }
}
