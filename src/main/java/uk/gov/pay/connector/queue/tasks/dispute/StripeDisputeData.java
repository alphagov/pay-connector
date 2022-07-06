package uk.gov.pay.connector.queue.tasks.dispute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;
import java.util.List;

import static uk.gov.pay.connector.util.DateTimeUtils.toUTCZonedDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeDisputeData {
    @JsonProperty("object")
    private String resourceType;
    @JsonProperty("payment_intent")
    private String paymentIntentId;
    @JsonProperty("amount")
    private Long amount;
    @JsonProperty("id")
    private String id;
    @JsonProperty("reason")
    private String reason;
    @JsonProperty("created")
    private Long created;
    @JsonProperty("balance_transactions")
    private List<BalanceTransaction> balanceTransactionList;
    @JsonProperty("evidence_details")
    private EvidenceDetails evidenceDetails;

    @JsonProperty("status")
    private String status;

    public String getResourceType() {
        return resourceType;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public Long getAmount() {
        return amount;
    }

    public String getId() {
        return id;
    }

    public String getReason() {
        return reason;
    }

    public ZonedDateTime getDisputeCreated() {
        return toUTCZonedDateTime(created);
    }

    public List<BalanceTransaction> getBalanceTransactionList() {
        return balanceTransactionList;
    }

    public EvidenceDetails getEvidenceDetails() {
        return evidenceDetails;
    }

    public String getStatus() {
        return status;
    }
}
