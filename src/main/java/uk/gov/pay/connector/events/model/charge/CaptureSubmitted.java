package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

// informational only
public class CaptureSubmitted extends PaymentEventWithoutDetails {
    public CaptureSubmitted(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
