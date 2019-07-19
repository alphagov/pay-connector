package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class CancelByExpirationFailed extends PaymentEventWithoutDetails {
    public CancelByExpirationFailed(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
