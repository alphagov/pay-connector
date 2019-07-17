package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class CancelledByExpiration extends PaymentEventWithoutDetails {
    public CancelledByExpiration(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
