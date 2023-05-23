package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class CancelledByExpiration extends PaymentEventWithoutDetails {
    public CancelledByExpiration(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, timestamp);
    }
}
