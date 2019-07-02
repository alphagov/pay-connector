package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class UserApprovedForCaptureAwaitingServiceApproval extends PaymentEventWithoutDetails {
    public UserApprovedForCaptureAwaitingServiceApproval(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
