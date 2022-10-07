package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class CaptureConfirmedByGatewayNotification extends PaymentEventWithoutDetails {
    public CaptureConfirmedByGatewayNotification(String serviceId, boolean live, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
