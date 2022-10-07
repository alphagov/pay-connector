package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.events.eventdetails.charge.RefundAvailabilityUpdatedEventDetails;

import java.time.Instant;

public class RefundAvailabilityUpdated extends PaymentEvent {

    public RefundAvailabilityUpdated(String serviceId, boolean live, String resourceExternalId,
                                     RefundAvailabilityUpdatedEventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static RefundAvailabilityUpdated from(LedgerTransaction ledgerTransaction,
                                                 ExternalChargeRefundAvailability externalChargeRefundAvailability, Instant timestamp) {
        var eventDetails = RefundAvailabilityUpdatedEventDetails.from(externalChargeRefundAvailability);
        return new RefundAvailabilityUpdated(ledgerTransaction.getServiceId(), ledgerTransaction.getLive(),
                ledgerTransaction.getTransactionId(), eventDetails, timestamp);
    }
}
