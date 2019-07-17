package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

// In rare circumstances.. only smartpay?
public class CaptureErrored extends PaymentEventWithoutDetails {
    public CaptureErrored(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
