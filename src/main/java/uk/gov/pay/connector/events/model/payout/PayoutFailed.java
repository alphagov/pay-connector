package uk.gov.pay.connector.events.model.payout;

import uk.gov.pay.connector.events.eventdetails.payout.PayoutFailedEventDetails;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;

import java.time.Instant;

public class PayoutFailed extends PayoutEvent {

    public PayoutFailed(String resourceExternalId, PayoutFailedEventDetails eventDetails, Instant timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    public static PayoutFailed from(Instant eventTimestamp, StripePayout payout) {
        return new PayoutFailed(payout.getId(),
                new PayoutFailedEventDetails(
                        payout.getStatus(),
                        payout.getFailureCode(),
                        payout.getFailureMessage(),
                        payout.getFailureBalanceTransaction()
                ),
                eventTimestamp);
    }
}
