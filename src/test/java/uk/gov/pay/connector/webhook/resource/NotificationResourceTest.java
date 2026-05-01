package uk.gov.pay.connector.webhook.resource;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class NotificationResourceTest {
    
    @InjectMocks
    private NotificationResource notificationResource;

    @Test
    void shouldHandleAdyenPaymentNotification() {
        String rawNotification = "{\n  \"notificationItems\": [\n    {\"NotificationRequestItem\": {\"eventCode\": \"AUTHORISATION\"}}\n  ]\n}";
        String forwardedIpAddress = "10.20.30.40";
        
        Response response = notificationResource.authoriseAdyenPaymentsNotifications(rawNotification, forwardedIpAddress);
            assertThat(response.getStatus()).isEqualTo(200);
    }
}

