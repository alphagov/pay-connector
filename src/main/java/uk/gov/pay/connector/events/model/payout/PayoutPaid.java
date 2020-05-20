package uk.gov.pay.connector.events.model.payout;

import uk.gov.pay.connector.events.eventdetails.payout.PayoutPaidEventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;

import java.time.ZonedDateTime;

public class PayoutPaid extends PayoutEvent {
    public PayoutPaid(String resourceExternalId, PayoutPaidEventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    public static Event from(ZonedDateTime eventTimestamp, StripePayout payout) {
        return new PayoutPaid(payout.getId(),
                new PayoutPaidEventDetails(payout.getArrivalDate(), payout.getStatus()),
                eventTimestamp);
    }
}
