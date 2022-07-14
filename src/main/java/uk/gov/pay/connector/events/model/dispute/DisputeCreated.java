package uk.gov.pay.connector.events.model.dispute;

import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeCreatedEventDetails;
import uk.gov.pay.connector.queue.tasks.dispute.BalanceTransaction;
import uk.gov.pay.connector.gateway.stripe.response.StripeDisputeData;

import java.time.ZonedDateTime;

import static uk.gov.pay.connector.util.RandomIdGenerator.idFromExternalId;

public class DisputeCreated extends DisputeEvent {
    public DisputeCreated(String resourceExternalId, String parentResourceExternalId, String serviceId,
                          Boolean live, DisputeCreatedEventDetails eventDetails, ZonedDateTime disputeCreated) {
        super(resourceExternalId, parentResourceExternalId, serviceId, live, eventDetails, disputeCreated);
    }

    public static DisputeCreated from(StripeDisputeData stripeDisputeData, LedgerTransaction transaction, ZonedDateTime disputeCreatedDate) {
        if (stripeDisputeData.getBalanceTransactionList().size() > 1) {
            throw new RuntimeException("Dispute data has too many balance_transactions");
        }
        BalanceTransaction balanceTransaction = stripeDisputeData.getBalanceTransactionList().get(0);
        DisputeCreatedEventDetails eventDetails = new DisputeCreatedEventDetails(
                Math.abs(balanceTransaction.getFee()),
                stripeDisputeData.getEvidenceDetails().getDueByTimestamp(),
                transaction.getGatewayAccountId(),
                stripeDisputeData.getAmount(),
                balanceTransaction.getNetAmount(),
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
