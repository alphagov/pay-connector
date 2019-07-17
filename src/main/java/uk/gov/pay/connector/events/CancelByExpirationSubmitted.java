package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class CancelByExpirationSubmitted extends PaymentEventWithoutDetails {
    public CancelByExpirationSubmitted(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
