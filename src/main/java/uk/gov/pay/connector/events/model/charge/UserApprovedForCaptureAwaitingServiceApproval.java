package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class UserApprovedForCaptureAwaitingServiceApproval extends PaymentEventWithoutDetails {
    public UserApprovedForCaptureAwaitingServiceApproval(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
