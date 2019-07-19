package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class CancelByUserFailed extends PaymentEventWithoutDetails {
    public CancelByUserFailed(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
