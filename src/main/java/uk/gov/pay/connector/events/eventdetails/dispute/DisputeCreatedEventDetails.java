package uk.gov.pay.connector.events.eventdetails.dispute;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.service.payments.commons.api.json.IsoInstantMicrosecondSerializer;

import java.time.Instant;
import java.util.Objects;

public class DisputeCreatedEventDetails extends DisputeEventDetails {
    @JsonSerialize(using = IsoInstantMicrosecondSerializer.class)
    private final Instant evidenceDueDate;
    private final Long amount;
    private final String reason;
    private final String gatewayTransactionId;

    public DisputeCreatedEventDetails(Instant evidenceDueDate, String gatewayAccountId, Long amount,
                                      String reason, String gatewayTransactionId) {
        super(gatewayAccountId);
        this.evidenceDueDate = evidenceDueDate;
        this.amount = amount;
        this.reason = reason;
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public Instant getEvidenceDueDate() {
        return evidenceDueDate;
    }

    public Long getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DisputeCreatedEventDetails that = (DisputeCreatedEventDetails) o;
        return Objects.equals(evidenceDueDate, that.evidenceDueDate) && Objects.equals(amount, that.amount) && Objects.equals(reason, that.reason) && Objects.equals(gatewayTransactionId, that.gatewayTransactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(evidenceDueDate, amount, reason, gatewayTransactionId);
    }
}
