package uk.gov.pay.connector.gateway.adyen.response.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaptureResponseBody(
        @JsonProperty("merchantAccount")
        String merchantAccount,
        @JsonProperty("paymentPspReference")
        String paymentPspReference,
        @JsonProperty("pspReference")
        String pspReference,
        @JsonProperty("status")
        String status,
        @JsonProperty("amount")
        Amount amount,
        @JsonProperty("message")
        String errorMessage) {
}
