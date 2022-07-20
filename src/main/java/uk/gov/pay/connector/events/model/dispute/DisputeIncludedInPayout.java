package uk.gov.pay.connector.events.model.dispute;

import uk.gov.pay.connector.events.eventdetails.TransactionIncludedInPayoutEventDetails;

import java.time.ZonedDateTime;

public class DisputeIncludedInPayout extends DisputeEvent {
    public DisputeIncludedInPayout(String disputeExternalId, String gatewayPayoutId, ZonedDateTime payoutCreatedDate) {
        super(disputeExternalId, new TransactionIncludedInPayoutEventDetails(gatewayPayoutId), payoutCreatedDate);
    }
}
