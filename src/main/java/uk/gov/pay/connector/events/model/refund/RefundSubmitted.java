package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithReferenceDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.ZonedDateTime;

public class RefundSubmitted extends RefundEvent {

    public RefundSubmitted(String resourceExternalId, String parentResourceExternalId,
                           RefundEventWithReferenceDetails referenceDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, parentResourceExternalId, referenceDetails, timestamp);
    }

    public static RefundSubmitted from(RefundHistory refundHistory) {
        return new RefundSubmitted(refundHistory.getExternalId(),
                refundHistory.getChargeEntity().getExternalId(),
                new RefundEventWithReferenceDetails(refundHistory.getReference()),
                refundHistory.getHistoryStartDate());
    }
}
