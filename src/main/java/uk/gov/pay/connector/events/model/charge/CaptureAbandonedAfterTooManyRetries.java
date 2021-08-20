package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class CaptureAbandonedAfterTooManyRetries extends PaymentEventWithoutDetails {
    public CaptureAbandonedAfterTooManyRetries(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
