package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StripeAccountResponse {

    @JsonProperty("stripe_account_id")
    @Schema(required = true, example = "acct_123example123")
    private final String stripeAccountId;

    public StripeAccountResponse(String stripeAccountId) {
        this.stripeAccountId = Objects.requireNonNull(stripeAccountId);
    }

    public String getStripeAccountId() {
        return stripeAccountId;
    }

}
