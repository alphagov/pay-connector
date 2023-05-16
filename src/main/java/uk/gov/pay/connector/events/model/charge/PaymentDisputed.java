package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentDisputedEventDetails;

import java.time.Instant;

public class PaymentDisputed extends PaymentEvent {
    private PaymentDisputed(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }
    
    public static PaymentDisputed from(LedgerTransaction ledgerTransaction, Instant disputeCreatedDate) {
        return new PaymentDisputed(
                ledgerTransaction.getServiceId(),
                ledgerTransaction.getLive(),
                Long.valueOf(ledgerTransaction.getGatewayAccountId()),
                ledgerTransaction.getTransactionId(),
                new PaymentDisputedEventDetails(),
                disputeCreatedDate
        );
    }
}
