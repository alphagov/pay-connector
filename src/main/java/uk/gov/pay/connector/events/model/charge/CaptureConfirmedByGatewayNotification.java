package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class CaptureConfirmedByGatewayNotification extends PaymentEventWithoutDetails {
    public CaptureConfirmedByGatewayNotification(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
