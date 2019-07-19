package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByServiceEventDetails;

import java.time.ZonedDateTime;

public class RefundCreatedByService extends RefundEvent {

    public RefundCreatedByService(String resourceExternalId, String parentResourceExternalId, RefundCreatedByServiceEventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, parentResourceExternalId, eventDetails, timestamp);
    }
}
