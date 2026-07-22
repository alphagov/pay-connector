package uk.gov.pay.connector.queue.tasks.handlers.adyen;

import com.adyen.model.notification.NotificationRequestItem;

import java.time.ZoneId;
import java.time.ZonedDateTime;

final class AdyenWebhookHandlerSupport {
    static final String GATEWAY_TRANSACTION_ID = "gateway_transaction_id";
    static final ZoneId UTC = ZoneId.of("UTC");

    private AdyenWebhookHandlerSupport() {
    }

    static ZonedDateTime eventDateInUtc(NotificationRequestItem item) {
        return ZonedDateTime.ofInstant(item.getEventDate().toInstant(), UTC);
    }
}
