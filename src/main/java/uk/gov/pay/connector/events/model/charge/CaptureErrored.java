package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

// In rare circumstances...
public class CaptureErrored extends PaymentEventWithoutDetails {
    public CaptureErrored(String serviceId, boolean live, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
