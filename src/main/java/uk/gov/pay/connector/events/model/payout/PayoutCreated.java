package uk.gov.pay.connector.events.model.payout;

import uk.gov.pay.connector.events.eventdetails.payout.PayoutCreatedEventDetails;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;

import java.time.Instant;

public class PayoutCreated extends PayoutEvent {

    private PayoutCreated(String resourceExternalId, PayoutCreatedEventDetails eventDetails, Instant timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    public static PayoutCreated from(Long gatewayAccountId, StripePayout payout) {
        return new PayoutCreated(
                payout.getId(),
                new PayoutCreatedEventDetails(
                        gatewayAccountId,
                        payout.getAmount(),
                        payout.getArrivalDate(),
                        payout.getStatus(),
                        payout.getType(),
                        payout.getStatementDescriptor()),
                        payout.getCreated());
    }
}
