package uk.gov.pay.connector.gateway.adyen.response.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdyenError(
        @JsonProperty("status")
        String status,
        @JsonProperty("message")
        String message,
        @JsonProperty("errorCode")
        String errorCode,
        @JsonProperty("errorType")
        String errorType,
        @JsonProperty("pspReference")
        String pspReference
) {
}
