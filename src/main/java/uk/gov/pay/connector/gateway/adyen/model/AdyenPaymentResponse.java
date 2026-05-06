package uk.gov.pay.connector.gateway.adyen.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gateway.adyen.model.json.Action;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdyenPaymentResponse(
        @JsonProperty("pspReference")
        String pspReference,
        @JsonProperty("resultCode")
        String resultCode,
        @JsonProperty("refusalReason")
        String refusalReason,
        @JsonProperty("refusalReasonCode")
        String refusalReasonCode,
        @JsonProperty("action")
        Action action
) {
}
