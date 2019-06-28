package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class CaptureConfirmedEvent extends PaymentEvent {
    public CaptureConfirmedEvent(String resourceExternalId, ZonedDateTime timestamp, ZonedDateTime gatewayEventDate) {
        super(resourceExternalId, new CaptureConfirmedEventDetails(gatewayEventDate), timestamp);
    }
}
