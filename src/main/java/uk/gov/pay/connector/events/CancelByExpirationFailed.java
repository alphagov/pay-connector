package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class CancelByExpirationFailed extends PaymentEventWithoutDetails {
    public CancelByExpirationFailed(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
