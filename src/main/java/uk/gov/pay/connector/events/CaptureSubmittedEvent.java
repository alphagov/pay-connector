package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

// informational only
public class CaptureSubmittedEvent extends PaymentEventWithoutDetails {
    public CaptureSubmittedEvent(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
