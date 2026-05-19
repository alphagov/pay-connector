package uk.gov.pay.connector.gateway.adyen.request.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CancelRequestPayload(
        @JsonProperty("reference")
        String reference,
        @JsonProperty("merchantAccount")
        String merchantAccount) {
}
