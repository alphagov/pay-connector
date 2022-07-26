package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentDisputedEventDetails;

import java.time.ZonedDateTime;

public class PaymentDisputed extends PaymentEvent {
    private PaymentDisputed(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }
    
    public static PaymentDisputed from(LedgerTransaction ledgerTransaction, ZonedDateTime disputeCreatedDate) {
        return new PaymentDisputed(
                ledgerTransaction.getServiceId(),
                ledgerTransaction.getLive(),
                ledgerTransaction.getTransactionId(),
                new PaymentDisputedEventDetails(),
                disputeCreatedDate
        );
    }
}
