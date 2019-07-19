package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class CancelByExternalServiceFailed extends PaymentEventWithoutDetails {
    public CancelByExternalServiceFailed(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
