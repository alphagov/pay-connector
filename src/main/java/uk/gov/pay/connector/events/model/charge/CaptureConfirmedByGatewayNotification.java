package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class CaptureConfirmedByGatewayNotification extends PaymentEventWithoutDetails {
    public CaptureConfirmedByGatewayNotification(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, timestamp);
    }
}
