package uk.gov.pay.connector.chargeevent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.jackson.JsonSnakeCase;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;

import java.time.Instant;

import static uk.gov.service.payments.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION;

@JsonSnakeCase
public record TransactionEvent (
        @JsonProperty("type")
        @Schema(example = "PAYMENT")
        Type type,
        
        @JsonIgnore
        String extChargeId,
        
        @JsonProperty("refund_reference")
        String refundGatewayTransactionId,

        @JsonProperty("state")
        State state,

        @JsonProperty("amount")
        @Schema(example = "100")
        Long amount,

        @JsonIgnore
        Instant updated,
        
        @JsonProperty("submitted_by")
        String userExternalId
) implements Comparable<TransactionEvent> {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSnakeCase
    public record State (
            @Schema(example = "cancelled")
            String status,
            
            @Schema(example = "true")
            boolean finished,
            
            @Schema(example = "P0040")
            String code,
            
            @Schema(example = "Payment was cancelled by service")
            String message
        ) {
        
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

    public static State extractState(ExternalChargeState externalChargeState) {
        return new State(
                externalChargeState.getStatus(),
                externalChargeState.isFinished(),
                externalChargeState.getCode(),
                externalChargeState.getMessage());
    }

    public static State extractState(ExternalRefundStatus externalRefundState) {
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

    public TransactionEvent(Type type, String extChargeId, State state, Long amount, Instant updated) {
        this(
                type,
                extChargeId,
                null,
                state,
                amount,
                updated,
                null
        );
    }
    
    @JsonProperty("updated")
    @Schema(example = "2022-06-28T10:41:40.460Z")
    public String getUpdated() {
        return ISO_INSTANT_MILLISECOND_PRECISION.format(updated);
    }

    @JsonIgnore
    public Instant getTimeUpdate() {
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
        if (refundGatewayTransactionId != null ? !refundGatewayTransactionId.equals(that.refundGatewayTransactionId) : that.refundGatewayTransactionId != null)
            return false;
        if (extChargeId != null ? !extChargeId.equals(that.extChargeId) : that.extChargeId != null) return false;
        if (state != null ? !state.equals(that.state) : that.state != null) return false;
        if (userExternalId != null ? !userExternalId.equals(that.userExternalId) : that.userExternalId != null)
            return false;
        return !(amount != null ? !amount.equals(that.amount) : that.amount != null);
    }


    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (refundGatewayTransactionId != null ? refundGatewayTransactionId.hashCode() : 0);
        result = 31 * result + (extChargeId != null ? extChargeId.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        return result;
    }
}
