package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class UserApprovedForCaptureAwaitingServiceApproval extends PaymentEventWithoutDetails {
    public UserApprovedForCaptureAwaitingServiceApproval(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
