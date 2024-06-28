package uk.gov.pay.connector.refund.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record RefundRequest (
    @JsonProperty("amount")
    @Schema(example = "3444", required = true, description = "Amount to refund in pence")
    long amount,

    @JsonProperty("refund_amount_available")
    @Schema(example = "30000", required = true, description = "Total amount still available before issuing the refund")
    long amountAvailableForRefund,

    @JsonProperty("user_external_id")
    @Schema(example = "3444", description = "The ID of the user who issued the refund")
    String userExternalId,

    @JsonProperty("user_email")
    @Schema(example = "joeb@example.org", description = "Email address of the user refunding payment")
    String userEmail
) {
    public RefundRequest(long amount, long amountAvailableForRefund, String userExternalId) {
        this(
            amount,
            amountAvailableForRefund,
            userExternalId,
            null    
        );
    }
}
