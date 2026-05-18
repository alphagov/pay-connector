package uk.gov.pay.connector.gateway.adyen.model.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentCancelRequest(
        @JsonProperty("reference")
        String reference,
        @JsonProperty("merchantAccount")
        String merchantAccount) {
}
