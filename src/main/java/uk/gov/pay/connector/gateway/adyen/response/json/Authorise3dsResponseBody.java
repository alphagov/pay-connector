package uk.gov.pay.connector.gateway.adyen.response.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.StringJoiner;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Authorise3dsResponseBody(
        @JsonProperty("pspReference")
        String pspReference,
        @JsonProperty("resultCode")
        String resultCode,
        @JsonProperty("additionalData")
        AdditionalData additionalData
) {
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "Adyen Authorise 3DS Response (", ")");
        if (isNotBlank(pspReference)) {
            joiner.add("pspReference: " + pspReference);
        }
        if (isNotBlank(resultCode)) {
            joiner.add("resultCode: " + resultCode);
        }
        return joiner.toString();
    }

    public String getStoredPaymentMethodId() {
        return additionalData == null ? null : additionalData.storedPaymentMethodId();
    }
}
