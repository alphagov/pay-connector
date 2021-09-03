package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class CancelledByExpiration extends PaymentEventWithoutDetails {
    public CancelledByExpiration(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
