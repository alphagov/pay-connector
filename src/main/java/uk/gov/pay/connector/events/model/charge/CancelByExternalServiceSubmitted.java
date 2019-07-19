package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class CancelByExternalServiceSubmitted extends PaymentEventWithoutDetails {
    public CancelByExternalServiceSubmitted(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
