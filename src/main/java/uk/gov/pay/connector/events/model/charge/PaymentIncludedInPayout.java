package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.TransactionIncludedInPayoutEventDetails;

import java.time.ZonedDateTime;

public class PaymentIncludedInPayout extends PaymentEvent {
    public PaymentIncludedInPayout(String paymentExternalId, String gatewayPayoutId, ZonedDateTime payoutCreatedDate) {
        super(paymentExternalId, new TransactionIncludedInPayoutEventDetails(gatewayPayoutId), payoutCreatedDate);
    }
}
