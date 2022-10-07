package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class CancelByUserSubmitted extends PaymentEventWithoutDetails {
    public CancelByUserSubmitted(String serviceId, boolean live, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
