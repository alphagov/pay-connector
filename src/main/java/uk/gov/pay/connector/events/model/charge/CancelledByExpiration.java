package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class CancelledByExpiration extends PaymentEventWithoutDetails {
    public CancelledByExpiration(String serviceId, boolean live, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
