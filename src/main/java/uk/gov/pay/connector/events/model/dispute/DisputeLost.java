package uk.gov.pay.connector.events.model.dispute;

import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeLostEventDetails;
import uk.gov.pay.connector.queue.tasks.dispute.BalanceTransaction;
import uk.gov.pay.connector.gateway.stripe.response.StripeDisputeData;

import java.time.ZonedDateTime;

import static uk.gov.pay.connector.util.RandomIdGenerator.idFromExternalId;

public class DisputeLost extends DisputeEvent {
    public DisputeLost(String resourceExternalId, String parentResourceExternalId, String serviceId, Boolean live,
                       DisputeLostEventDetails eventDetails, ZonedDateTime eventDate) {
        super(resourceExternalId, parentResourceExternalId, serviceId, live, eventDetails, eventDate);
    }

    public static DisputeLost from(StripeDisputeData stripeDisputeData, ZonedDateTime eventDate, LedgerTransaction transaction) {
        if (stripeDisputeData.getBalanceTransactionList().size() > 1) {
            throw new RuntimeException("Dispute data has too many balance_transactions");
        }
        BalanceTransaction balanceTransaction = stripeDisputeData.getBalanceTransactionList().get(0);
        DisputeLostEventDetails eventDetails = new DisputeLostEventDetails(
                transaction.getGatewayAccountId(),
                balanceTransaction.getNetAmount(),
                stripeDisputeData.getAmount(),
                Math.abs(balanceTransaction.getFee()));

        return new DisputeLost(idFromExternalId(stripeDisputeData.getId()),
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
