package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class CaptureAbandonedAfterTooManyRetries extends PaymentEventWithoutDetails {
    public CaptureAbandonedAfterTooManyRetries(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
