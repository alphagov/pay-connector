package uk.gov.pay.connector.queue.tasks.handlers.adyen;

import com.adyen.model.notification.NotificationRequestItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdyenWebhookHandlerSupportTest {

    @Mock
    private NotificationRequestItem mockNotificationRequestItem;

    @Test
    void should_convert_event_date_to_utc() {
        Instant eventInstant = Instant.parse("2026-05-19T10:15:30Z");
        when(mockNotificationRequestItem.getEventDate()).thenReturn(Date.from(eventInstant));

        ZonedDateTime eventDateInUtc = AdyenWebhookHandlerSupport.eventDateInUtc(mockNotificationRequestItem);

        assertThat(eventDateInUtc, is(ZonedDateTime.ofInstant(eventInstant, ZoneId.of("UTC"))));
    }
}
