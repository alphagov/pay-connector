package uk.gov.pay.connector.events.model.refund;

import java.time.ZonedDateTime;

public class RefundSucceeded extends RefundEvent {

    public RefundSucceeded(String resourceExternalId, String parentResourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, parentResourceExternalId, timestamp);
    }
}
