package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.stripe.model.Payout;

import java.time.Instant;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StripePayout {
    String id;
    Long amount;
    Long arrivalDate;
    Long created;
    String status;
    String type;
    String statementDescriptor;
    private String failureCode;
    private String failureBalanceTransaction;
    private String failureMessage;

    public StripePayout() {
    }

    public StripePayout(String id, Long amount, Long arrivalDate, Long created, String status, String type, String statementDescriptor) {
        this.id = id;
        this.amount = amount;
        this.arrivalDate = arrivalDate;
        this.created = created;
        this.status = status;
        this.type = type;
        this.statementDescriptor = statementDescriptor;
    }

    public StripePayout(String id, String status, String failureCode, String failureMessage, String failureBalanceTransaction) {
        this.id = id;
        this.status = status;
        this.failureCode = failureCode;
        this.failureBalanceTransaction = failureBalanceTransaction;
        this.failureMessage = failureMessage;
    }

    public static StripePayout from(Payout payoutObject) {
        StripePayout stripePayout = new StripePayout(payoutObject.getId(), payoutObject.getAmount(),
                payoutObject.getArrivalDate(), payoutObject.getCreated(),
                payoutObject.getStatus(), payoutObject.getType(),
                payoutObject.getStatementDescriptor());
        stripePayout.failureMessage = payoutObject.getFailureMessage();
        stripePayout.failureCode = payoutObject.getFailureCode();
        stripePayout.failureBalanceTransaction = payoutObject.getFailureBalanceTransaction();

        return stripePayout;
    }

    public String getId() {
        return id;
    }

    public Long getAmount() {
        return amount;
    }

    public Instant getCreated() {
        return Instant.ofEpochSecond(created);
    }

    public Instant getArrivalDate() {
        return Instant.ofEpochSecond(arrivalDate);
    }

    public String getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public String getStatementDescriptor() {
        return statementDescriptor;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureBalanceTransaction() {
        return failureBalanceTransaction;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StripePayout payout = (StripePayout) o;
        return id.equals(payout.id) &&
                amount.equals(payout.amount) &&
                Objects.equals(arrivalDate, payout.arrivalDate) &&
                created.equals(payout.created) &&
                Objects.equals(status, payout.status) &&
                Objects.equals(type, payout.type) &&
                Objects.equals(statementDescriptor, payout.statementDescriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, amount, arrivalDate, created, status, type, statementDescriptor);
    }
}
