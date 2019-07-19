package uk.gov.pay.connector.events.model.refund;

import java.time.ZonedDateTime;

public class RefundError extends RefundEvent {

    public RefundError(String resourceExternalId, String parentResourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, parentResourceExternalId, timestamp);
    }
}
