package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.TransactionIncludedInPayoutEventDetails;

import java.time.Instant;

public class PaymentIncludedInPayout extends PaymentEvent {
    public PaymentIncludedInPayout(String paymentExternalId, String gatewayPayoutId, Instant payoutCreatedDate) {
        super(paymentExternalId, new TransactionIncludedInPayoutEventDetails(gatewayPayoutId), payoutCreatedDate);
    }
}
