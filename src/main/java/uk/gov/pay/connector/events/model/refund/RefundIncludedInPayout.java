package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.events.eventdetails.TransactionIncludedInPayoutEventDetails;

import java.time.Instant;

public class RefundIncludedInPayout extends RefundEvent {
    public RefundIncludedInPayout(String refundExternalId, String gatewayPayoutId, Instant payoutCreatedDate) {
        super(refundExternalId, new TransactionIncludedInPayoutEventDetails(gatewayPayoutId), payoutCreatedDate);
    }
}
