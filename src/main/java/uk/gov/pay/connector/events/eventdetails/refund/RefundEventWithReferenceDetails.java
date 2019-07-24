package uk.gov.pay.connector.events.eventdetails.refund;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class RefundEventWithReferenceDetails extends EventDetails {

    private String reference;

    public RefundEventWithReferenceDetails(String reference) {
        this.reference = reference;
    }

    public String getReference() {
        return reference;
    }
}
