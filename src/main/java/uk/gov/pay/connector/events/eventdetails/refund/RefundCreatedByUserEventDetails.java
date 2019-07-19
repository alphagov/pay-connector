package uk.gov.pay.connector.events.eventdetails.refund;

public class RefundCreatedByUserEventDetails extends RefundCreatedEventDetails {

    private String refundedBy;

    public RefundCreatedByUserEventDetails(Long refundAmount, String refundedBy) {
        super(refundAmount);
        this.refundedBy = refundedBy;
    }

    public String getRefundedBy() {
        return refundedBy;
    }
}
