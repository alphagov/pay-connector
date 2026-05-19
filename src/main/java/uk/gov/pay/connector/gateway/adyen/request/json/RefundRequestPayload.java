package uk.gov.pay.connector.gateway.adyen.request.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefundRequestPayload(
        @JsonProperty("amount")
        Amount amount,
        @JsonProperty("merchantAccount")
        String merchantAccount,
        @JsonProperty("reference")
        String reference,
        @JsonProperty("store")
        String storeId
) {
}   
