package uk.gov.pay.connector.gateway.adyen.model.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Action(
        @JsonProperty("type")
        String type,
        @JsonProperty("url")
        String url,
        @JsonProperty("method")
        String method,
        @JsonProperty("data")
        Map<String, String> data,
        @JsonProperty("paymentData")
        String paymentData
) {
}
