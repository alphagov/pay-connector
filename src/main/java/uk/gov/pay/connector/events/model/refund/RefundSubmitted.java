package uk.gov.pay.connector.events.model.refund;

import java.time.ZonedDateTime;

public class RefundSubmitted extends RefundEvent {

    public RefundSubmitted(String resourceExternalId, String parentResourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, parentResourceExternalId, timestamp);
    }
}
