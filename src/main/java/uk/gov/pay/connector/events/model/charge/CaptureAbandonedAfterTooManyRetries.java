package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class CaptureAbandonedAfterTooManyRetries extends PaymentEventWithoutDetails {
    public CaptureAbandonedAfterTooManyRetries(String serviceId, boolean live, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
