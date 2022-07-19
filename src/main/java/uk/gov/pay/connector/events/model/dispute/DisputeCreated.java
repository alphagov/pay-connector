package uk.gov.pay.connector.events.model.dispute;

import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeCreatedEventDetails;
import uk.gov.pay.connector.gateway.stripe.response.StripeDisputeData;

import java.time.ZonedDateTime;

import static uk.gov.pay.connector.util.RandomIdGenerator.idFromExternalId;

public class DisputeCreated extends DisputeEvent {
    public DisputeCreated(String resourceExternalId, String parentResourceExternalId, String serviceId,
                          Boolean live, DisputeCreatedEventDetails eventDetails, ZonedDateTime disputeCreated) {
        super(resourceExternalId, parentResourceExternalId, serviceId, live, eventDetails, disputeCreated);
    }

    public static DisputeCreated from(StripeDisputeData stripeDisputeData, LedgerTransaction transaction, ZonedDateTime disputeCreatedDate) {
        DisputeCreatedEventDetails eventDetails = new DisputeCreatedEventDetails(
                stripeDisputeData.getEvidenceDetails().getEvidenceDueByDate(),
                transaction.getGatewayAccountId(),
                stripeDisputeData.getAmount(),
                stripeDisputeData.getReason());

        return new DisputeCreated(
                idFromExternalId(stripeDisputeData.getId()),
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
