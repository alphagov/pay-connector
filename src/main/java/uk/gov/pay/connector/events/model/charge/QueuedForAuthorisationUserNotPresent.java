package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class QueuedForAuthorisationUserNotPresent extends PaymentEventWithoutDetails {
    public QueuedForAuthorisationUserNotPresent(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
