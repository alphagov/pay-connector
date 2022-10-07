package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class UserApprovedForCaptureAwaitingServiceApproval extends PaymentEventWithoutDetails {
    public UserApprovedForCaptureAwaitingServiceApproval(String serviceId, boolean live, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
