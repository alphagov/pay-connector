package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class CancelledByUser extends PaymentEventWithoutDetails {
    public CancelledByUser(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
