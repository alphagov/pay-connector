package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByUserEventDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.ZonedDateTime;

public class RefundCreatedByUser extends RefundEvent {

    public RefundCreatedByUser(String resourceExternalId, String parentResourceExternalId, RefundCreatedByUserEventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, parentResourceExternalId, eventDetails, timestamp);
    }

    public static RefundCreatedByUser from(RefundHistory refundHistory) {
        return new RefundCreatedByUser(
                refundHistory.getExternalId(),
                refundHistory.getChargeEntity().getExternalId(),
                new RefundCreatedByUserEventDetails(
                        refundHistory.getAmount(),
                        refundHistory.getUserExternalId()),
                refundHistory.getHistoryStartDate());
    }
}
