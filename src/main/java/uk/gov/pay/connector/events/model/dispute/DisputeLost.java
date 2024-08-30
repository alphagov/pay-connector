package uk.gov.pay.connector.events.model.dispute;

import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeLostEventDetails;
import uk.gov.pay.connector.gateway.stripe.json.StripeDisputeData;

import java.time.Instant;

/**
 * This event represents a dispute that was lost by a service.
 */
public class DisputeLost extends DisputeEvent {
    public DisputeLost(String resourceExternalId, String parentResourceExternalId, String serviceId, Boolean live,
                       DisputeLostEventDetails eventDetails, Instant eventDate) {
        super(resourceExternalId, parentResourceExternalId, serviceId, live, eventDetails, eventDate);
    }

    public static DisputeLost from(String disputeExternalId, StripeDisputeData stripeDisputeData, Instant eventDate,
                                   LedgerTransaction transaction, boolean rechargedToService, long netAmount, long fee) {
        DisputeLostEventDetails eventDetails;
        if (rechargedToService) {
            eventDetails = new DisputeLostEventDetails(
                    transaction.getGatewayAccountId(),
                    stripeDisputeData.getAmount(),
                    netAmount,
                    fee);
        } else {
            eventDetails = new DisputeLostEventDetails(
                    transaction.getGatewayAccountId(),
                    stripeDisputeData.getAmount()
            );
        }
        
        return new DisputeLost(disputeExternalId,
                transaction.getTransactionId(),
                transaction.getServiceId(),
                transaction.getLive(),
                eventDetails,
                eventDate);
    }

    @Override
    public String toString() {
        return "DisputeLost{" +
                "resourceExternalId=" + getResourceExternalId() +
                ", eventDetails=" + getEventDetails() +
                ", timestamp=" + getTimestamp() +
                ", parentResourceExternalId= " + getParentResourceExternalId() +
                ", serviceId=" + getServiceId() +
                ", live=" + getLive() +
                '}';
    }
}
