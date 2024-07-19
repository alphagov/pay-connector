package uk.gov.pay.connector.refund.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The refund_amount_available field is a mechanism that acts as idempotency-lite, which is especially important when 
 * performing partial refunds. By having the consumer specify the amount available to refund at the time of the call, 
 * one can check to see if there’s still that much refundable before performing the refund.
 * <p> 
 * For example if a request goes through and gets to connector but then the HTTP response fails and terminates early, 
 * the client might retry the refund that they thought failed; because they’d still be passing the original 
 * “amount left to refund” connector can reject it because that refund has already been processed.
 * <p>
 * Example:
 * Payment: £20
 * <p>
 * /refund 
 * - amount £5
 * - amount available: £20
 * :green-tick: 
 * <p>
 * /refund
 * - amount £5
 * - amount available: £20 
 * :fail: 
 * <p>
 * /refund
 * - amount £5
 * - amount available: £15
 * :green-tick:
 */
public class RefundRequest {

    @JsonProperty("amount")
    @Schema(example = "3444", required = true, description = "Amount to refund in pence")
    private long amount;

    @JsonProperty("refund_amount_available")
    @Schema(example = "30000", required = true, description = "Total amount still available before issuing the refund")
    private long amountAvailableForRefund;

    @JsonProperty("user_external_id")
    @Schema(example = "3444", description = "The ID of the user who issued the refund")
    private String userExternalId;

    @JsonProperty("user_email")
    @Schema(example = "joeb@example.org", description = "Email address of the user refunding payment")
    private String userEmail;

    public RefundRequest() {
    }

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

    public String getUserExternalId() {
        return userExternalId;
    }

    public String getUserEmail() {
        return userEmail;
    }
}
