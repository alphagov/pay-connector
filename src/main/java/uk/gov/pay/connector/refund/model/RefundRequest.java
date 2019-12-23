package uk.gov.pay.connector.refund.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RefundRequest {

    @JsonProperty("amount")
    private long amount;

    @JsonProperty("refund_amount_available")
    private long amountAvailableForRefund;

    @JsonProperty("user_external_id")
    private String userExternalId;

    @JsonProperty("user_email")
    private String userEmail;

    public RefundRequest() {}

    public RefundRequest(long amount, long amountAvailableForRefund, String userExternalId) {
        this.amount = amount;
        this.amountAvailableForRefund = amountAvailableForRefund;
        this.userExternalId = userExternalId;
    }

    public long getAmount() {
        return amount;
    }

    public long getAmountAvailableForRefund() {
        return amountAvailableForRefund;
    }

    public String getUserExternalId() { return userExternalId; }

    public String getUserEmail() { return userEmail; }
}
