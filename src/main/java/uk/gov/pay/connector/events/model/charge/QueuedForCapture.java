package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class QueuedForCapture extends PaymentEventWithoutDetails {
    public QueuedForCapture(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, timestamp);
    }
}
