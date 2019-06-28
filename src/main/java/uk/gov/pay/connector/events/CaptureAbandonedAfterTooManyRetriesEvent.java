package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class CaptureAbandonedAfterTooManyRetriesEvent extends PaymentEventWithoutDetails {
    public CaptureAbandonedAfterTooManyRetriesEvent(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
