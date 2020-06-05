package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.events.eventdetails.TransactionIncludedInPayoutEventDetails;

import java.time.ZonedDateTime;

public class RefundIncludedInPayout extends RefundEvent {
    public RefundIncludedInPayout(String refundExternalId, String gatewayPayoutId, ZonedDateTime payoutCreatedDate) {
        super(refundExternalId, new TransactionIncludedInPayoutEventDetails(gatewayPayoutId), payoutCreatedDate);
    }
}
