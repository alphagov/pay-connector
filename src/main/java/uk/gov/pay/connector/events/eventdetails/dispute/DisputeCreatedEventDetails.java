package uk.gov.pay.connector.events.eventdetails.dispute;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.service.payments.commons.api.json.MicrosecondPrecisionDateTimeSerializer;

import java.time.ZonedDateTime;

public class DisputeCreatedEventDetails extends DisputeEventDetails {
    @JsonSerialize(using = MicrosecondPrecisionDateTimeSerializer.class)
    private final ZonedDateTime evidenceDueDate;
    private final Long amount;
    private final String reason;
    private final String gatewayTransactionId;

    public DisputeCreatedEventDetails(ZonedDateTime evidenceDueDate, String gatewayAccountId, Long amount,
                                      String reason, String gatewayTransactionId) {
        super(gatewayAccountId);
        this.evidenceDueDate = evidenceDueDate;
        this.amount = amount;
        this.reason = reason;
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public ZonedDateTime getEvidenceDueDate() {
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
}
