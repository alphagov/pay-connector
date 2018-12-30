package uk.gov.pay.connector.chargeevent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.jackson.JsonSnakeCase;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.ZonedDateTime;
import java.util.Objects;

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

            return Objects.equals(status, state.status);

        }

        @Override
        public int hashCode() {
            return Objects.hash(status);
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

        return (type != that.type)
            && Objects.equals(extRefundReference, that.extRefundReference)
            && Objects.equals(extChargeId, that.extChargeId)
            && Objects.equals(state, that.state)
            && Objects.equals(userExternalId, that.userExternalId)
            && Objects.equals(amount, that.amount);
    }


    @Override
    public int hashCode() {
        return Objects.hash(type, extRefundReference, extChargeId, state, amount);
    }
}
