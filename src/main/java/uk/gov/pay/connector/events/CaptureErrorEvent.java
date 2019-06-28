package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

// In rare circumstances.. only smartpay?
public class CaptureErrorEvent extends PaymentEventWithoutDetails {
    public CaptureErrorEvent(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
