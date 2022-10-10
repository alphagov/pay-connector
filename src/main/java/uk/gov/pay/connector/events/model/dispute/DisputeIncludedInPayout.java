package uk.gov.pay.connector.events.model.dispute;

import uk.gov.pay.connector.events.eventdetails.TransactionIncludedInPayoutEventDetails;

import java.time.Instant;

public class DisputeIncludedInPayout extends DisputeEvent {
    public DisputeIncludedInPayout(String disputeExternalId, String gatewayPayoutId, Instant payoutCreatedDate) {
        super(disputeExternalId, new TransactionIncludedInPayoutEventDetails(gatewayPayoutId), payoutCreatedDate);
    }
}
