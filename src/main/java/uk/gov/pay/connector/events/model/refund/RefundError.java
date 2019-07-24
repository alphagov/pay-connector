package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithReferenceDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.ZonedDateTime;

public class RefundError extends RefundEvent {

    public RefundError(String resourceExternalId, String parentResourceExternalId,
                       RefundEventWithReferenceDetails referenceDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, parentResourceExternalId, referenceDetails, timestamp);
    }

    public RefundError from(RefundHistory refundHistory) {
        return new RefundError(refundHistory.getExternalId(),
                refundHistory.getChargeEntity().getExternalId(),
                new RefundEventWithReferenceDetails(refundHistory.getReference()),
                refundHistory.getHistoryStartDate());
    }
}
