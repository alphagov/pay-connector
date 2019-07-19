package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class UserApprovedForCapture extends PaymentEventWithoutDetails {
    public UserApprovedForCapture(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
