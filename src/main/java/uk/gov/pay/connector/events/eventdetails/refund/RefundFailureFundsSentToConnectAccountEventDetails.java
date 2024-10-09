package uk.gov.pay.connector.events.eventdetails.refund;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class RefundFailureFundsSentToConnectAccountEventDetails extends EventDetails {

    private final long amount;
    private final String reference;
    private final String description;
    private final String adminGithubId;
    private final String updatedReason;
    private final String paymentProvider;
    private final long gatewayAccountId;
    private final String gatewayTransactionId;
    private final String zendeskId;


    public RefundFailureFundsSentToConnectAccountEventDetails(
            long amount,
            String reference,
            String description,
            String adminGithubId,
            String updatedReason,
            String paymentProvider,
            long gatewayAccountId,
            String gatewayTransactionId,
            String zendeskId) {
        this.amount = amount;
        this.reference = reference;
        this.description = description;
        this.adminGithubId = adminGithubId;
        this.updatedReason = updatedReason;
        this.paymentProvider = paymentProvider;
        this.gatewayAccountId = gatewayAccountId;
        this.gatewayTransactionId = gatewayTransactionId;
        this.zendeskId = zendeskId;
    }

    public long getAmount() {
        return amount;
    }

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public String getAdminGithubId() {
        return adminGithubId;
    }

    public String getUpdatedReason() {
        return updatedReason;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getZendeskId() {
        return zendeskId;
    }
}
