package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class CancelByExpirationSubmitted extends PaymentEventWithoutDetails {
    public CancelByExpirationSubmitted(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
