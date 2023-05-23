package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class CancelByExpirationSubmitted extends PaymentEventWithoutDetails {
    public CancelByExpirationSubmitted(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, timestamp);
    }
}
