package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class CancelByExternalServiceSubmitted extends PaymentEventWithoutDetails {
    public CancelByExternalServiceSubmitted(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
