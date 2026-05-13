package uk.gov.pay.connector.gateway.adyen.model.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Capture(
        @JsonProperty("amount")
        Amount amount,
        @JsonProperty("merchantAccount")
        String merchantAccountId) {
}
