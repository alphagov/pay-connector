package uk.gov.pay.connector.chargeevent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.jackson.JsonSnakeCase;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.ZonedDateTime;

@JsonSnakeCase
public class TransactionEvent implements Comparable<TransactionEvent> {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSnakeCase
    static public class State {
        private final String status;
        private final boolean finished;
        private final String code;
        private final String message;

        public State(String status, boolean finished, String code, String message) {
            this.status = status;
            this.finished = finished;
            this.code = code;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public boolean isFinished() {
            return finished;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            State state = (State) o;

            return !(status != null ? !status.equals(state.status) : state.status != null);

        }

        @Override
        public int hashCode() {
            return status != null ? status.hashCode() : 0;
        }
    }

    static public State extractState(ExternalChargeState externalChargeState) {
        return new State(
                externalChargeState.getStatus(),
                externalChargeState.isFinished(),
                externalChargeState.getCode(),
                externalChargeState.getMessage());
    }

    static public State extractState(ExternalRefundStatus externalRefundState) {
        return new State(
                externalRefundState.getStatus(),
                externalRefundState.isFinished(),
                null,
                null);
    }

    public enum Type {
        PAYMENT,
        REFUND
    }

    private final Type type;
    private String extChargeId;
    private String extRefundReference;
    private String userExternalId;
    private State state;
    private Long amount;
    private ZonedDateTime updated;

    public TransactionEvent(Type type, String extChargeId, State state, Long amount, ZonedDateTime updated) {
        this.type = type;
        this.extChargeId = extChargeId;
        this.state = state;
        this.amount = amount;
        this.updated = updated;
    }

    public TransactionEvent(Type type, String extChargeId, String extRefundReference, State state, Long amount, ZonedDateTime updated, String userExternalId) {
        this.type = type;
        this.extRefundReference = extRefundReference;
        this.extChargeId = extChargeId;
        this.state = state;
        this.amount = amount;
        this.updated = updated;
        this.userExternalId = userExternalId;
    }

    @JsonProperty("type")
    public Type getType() {
        return type;
    }

    @JsonProperty("refund_reference")
    public String getRefundId() {
        return extRefundReference;
    }

    @JsonProperty("submitted_by")
    public String getUserExternalId() {
        return userExternalId;
    }

    @JsonIgnore
    public String getChargeId() {
        return extChargeId;
    }

    public void setChargeId(String chargeId) {
        this.extChargeId = chargeId;
    }

    @JsonProperty("state")
    public State getState() {
        return state;
    }

    @JsonProperty("amount")
    public Long getAmount() {
        return amount;
    }

    @JsonProperty("updated")
    public String getUpdated() {
        return DateTimeUtils.toUTCDateTimeString(updated);
    }

    @JsonIgnore
    public ZonedDateTime getTimeUpdate() {
        return updated;
    }

    @Override
    public int compareTo(TransactionEvent transactionEvent) {
        if (this.getUpdated() != null) {
            return this.getUpdated().compareTo(transactionEvent.getUpdated());
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransactionEvent that = (TransactionEvent) o;

        if (type != that.type) return false;
        if (extRefundReference != null ? !extRefundReference.equals(that.extRefundReference) : that.extRefundReference != null) return false;
        if (extChargeId != null ? !extChargeId.equals(that.extChargeId) : that.extChargeId != null) return false;
        if (state != null ? !state.equals(that.state) : that.state != null) return false;
        if (userExternalId != null ? !userExternalId.equals(that.userExternalId) : that.userExternalId != null) return false;
        return !(amount != null ? !amount.equals(that.amount) : that.amount != null);
    }


    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (extRefundReference != null ? extRefundReference.hashCode() : 0);
        result = 31 * result + (extChargeId != null ? extChargeId.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        return result;
    }
}
