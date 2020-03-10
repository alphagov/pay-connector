package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class CaptureConfirmedByGatewayNotification extends PaymentEventWithoutDetails {
    public CaptureConfirmedByGatewayNotification(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
