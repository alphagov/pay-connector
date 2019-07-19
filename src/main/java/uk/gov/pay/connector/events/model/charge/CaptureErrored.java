package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

// In rare circumstances.. only smartpay?
public class CaptureErrored extends PaymentEventWithoutDetails {
    public CaptureErrored(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
