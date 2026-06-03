package uk.gov.pay.connector.gateway.adyen.response.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefundResponseBody(
        @JsonProperty("merchantAccount")
        String merchantAccount,
        @JsonProperty("paymentPspReference")
        String paymentPspReference,
        /*
          Adyen's unique reference associated with this refund request.
         */
        @JsonProperty("pspReference")
        String pspReference,
        @JsonProperty("reference")
        String reference,
        @JsonProperty("status")
        String status
) {
}
