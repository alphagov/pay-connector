package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class QueuedForAuthorisationWithUserNotPresent extends PaymentEventWithoutDetails {
    public QueuedForAuthorisationWithUserNotPresent(String serviceId, boolean live, Long gatewayAccountInternalId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountInternalId, resourceExternalId, timestamp);
    }
}
