package uk.gov.pay.connector.events.eventdetails.refund;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

abstract class RefundCreatedEventDetails extends EventDetails {

    private Long refundAmount;

    public RefundCreatedEventDetails(Long refundAmount) {
        this.refundAmount = refundAmount;
    }

    public Long getRefundAmount() {
        return refundAmount;
    }
}
