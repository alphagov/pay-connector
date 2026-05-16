package uk.gov.pay.connector.gateway.adyen.request.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefundRequestPayload(
        @JsonProperty("merchantAccount")
        String merchantAccountId,
        @JsonProperty("amount")
        Amount amount,
        @JsonProperty("reference")
        String reference,
        @JsonProperty("store")
        String storeId
) {
}   
