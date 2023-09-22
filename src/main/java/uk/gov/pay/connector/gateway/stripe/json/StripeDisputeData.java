package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.queue.tasks.dispute.BalanceTransaction;
import uk.gov.pay.connector.queue.tasks.dispute.Evidence;
import uk.gov.pay.connector.queue.tasks.dispute.EvidenceDetails;

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
    @JsonProperty("evidence")
    private Evidence evidence;

    @JsonProperty("status")
    private String status;

    @JsonProperty("livemode")
    private Boolean liveMode;

    public StripeDisputeData() {
        // for  jackson
    }

    public StripeDisputeData(String id, String paymentIntentId, String status,
                             Long amount, String reason, Long created,
                             List<BalanceTransaction> balanceTransactionList,
                             EvidenceDetails evidenceDetails,
                             Evidence evidence, Boolean liveMode
    ) {
        this.id = id;
        this.status = status;
        this.paymentIntentId = paymentIntentId;
        this.amount = amount;
        this.reason = reason;
        this.created = created;
        this.balanceTransactionList = balanceTransactionList;
        this.evidenceDetails = evidenceDetails;
        this.evidence = evidence;
        this.liveMode = liveMode;
    }

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

    public Evidence getEvidence() {
        return evidence;
    }

    public Boolean getLiveMode() {
        return liveMode;
    }
}
