package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class CancelByUserSubmitted extends PaymentEventWithoutDetails {
    public CancelByUserSubmitted(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
