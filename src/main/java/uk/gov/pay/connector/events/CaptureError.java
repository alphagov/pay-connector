package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

// In rare circumstances.. only smartpay?
public class CaptureError extends PaymentEventWithoutDetails {
    public CaptureError(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
