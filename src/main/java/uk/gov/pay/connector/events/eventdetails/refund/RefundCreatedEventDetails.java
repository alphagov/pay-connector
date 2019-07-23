package uk.gov.pay.connector.events.eventdetails.refund;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

abstract class RefundCreatedEventDetails extends EventDetails {

    private Long amount;

    public RefundCreatedEventDetails(Long amount) {
        this.amount = amount;
    }

    public Long getAmount() {
        return amount;
    }
}
