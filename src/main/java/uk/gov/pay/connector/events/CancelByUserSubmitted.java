package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class CancelByUserSubmitted extends PaymentEventWithoutDetails {
    public CancelByUserSubmitted(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
