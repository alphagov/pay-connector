package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class QueuedForAuthorisationWithUserNotPresent extends PaymentEventWithoutDetails {
    public QueuedForAuthorisationWithUserNotPresent(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
