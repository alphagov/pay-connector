package uk.gov.pay.connector.gateway.adyen.request.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentMethod(
        @JsonProperty("cvc")
        String cvc,
        @JsonProperty("expiryMonth")
        String expiryMonth,
        @JsonProperty("expiryYear")
        String expiryYear,
        @JsonProperty("holderName")
        String holderName,
        @JsonProperty("number")
        String number,
        @JsonProperty("type")
        String type) {
}
