package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class UserApprovedForCapture extends PaymentEventWithoutDetails {
    public UserApprovedForCapture(String serviceId, boolean isLive, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, isLive, resourceExternalId, timestamp);
    }
}
