package uk.gov.pay.connector.events.model.refund;

import java.time.ZonedDateTime;

public class RefundSuccessful extends RefundEvent {

    public RefundSuccessful(String resourceExternalId, String parentResourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, parentResourceExternalId, timestamp);
    }
}
