package uk.gov.pay.connector.gateway.adyen.request.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CaptureRequestPayload(
        @JsonProperty("amount")
        Amount amount,
        @JsonProperty("merchantAccount")
        String merchantAccountId) {
}
