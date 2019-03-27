package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gateway.model.response.RecoupFeeResponse;

public class StripeRecoupFeeResponse {
    @JsonProperty("amount")
    private Long amount;

    public RecoupFeeResponse toRecoupFeeResponse() {
        return new RecoupFeeResponse(true, amount, null);
    }
}
