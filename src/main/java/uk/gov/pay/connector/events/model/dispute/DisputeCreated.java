package uk.gov.pay.connector.events.model.dispute;

import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeCreatedEventDetails;
import uk.gov.pay.connector.gateway.stripe.json.StripeDisputeData;

import java.time.Instant;

public class DisputeCreated extends DisputeEvent {
    public DisputeCreated(String resourceExternalId, String parentResourceExternalId, String serviceId,
                          Boolean live, DisputeCreatedEventDetails eventDetails, Instant disputeCreated) {
        super(resourceExternalId, parentResourceExternalId, serviceId, live, eventDetails, disputeCreated);
    }

    public static DisputeCreated from(String disputeExternalId, StripeDisputeData stripeDisputeData,
                                      LedgerTransaction transaction, Instant disputeCreatedDate) {
        DisputeCreatedEventDetails eventDetails = new DisputeCreatedEventDetails(
                stripeDisputeData.getEvidenceDetails().getEvidenceDueByDate(),
                transaction.getGatewayAccountId(),
                stripeDisputeData.getAmount(),
                stripeDisputeData.getReason(),
                stripeDisputeData.getId());

        return new DisputeCreated(
                disputeExternalId,
                transaction.getTransactionId(),
                transaction.getServiceId(),
                transaction.getLive(),
                eventDetails,
                disputeCreatedDate);
    }

    @Override
    public String toString() {
        return "DisputeCreated{" +
                "resourceExternalId=" + getResourceExternalId() +
                ", eventDetails=" + getEventDetails() +
                ", timestamp=" + getTimestamp() +
                ", parentResourceExternalId= " + getParentResourceExternalId() +
                ", serviceId=" + getServiceId() +
                ", live=" + getLive() +
                '}';
    }
}
